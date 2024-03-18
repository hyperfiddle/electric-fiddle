(ns datagrid.schema
  "Schema manipulation utils"
  (:require
   [malli.core]
   [malli.registry]
   [malli.util]))

;;; Malli

(defn to-schema
  ([schema-definition] (to-schema nil schema-definition))
  ([registry schema-definition]
   (if (malli.core/schema? schema-definition)
     schema-definition
     (malli.core/schema schema-definition (if registry {:registry registry} {})))))

(defn registry
  ([] malli.core/default-registry)
  ([map-or-reg]
   (if (malli.registry/registry? map-or-reg)
     map-or-reg
     (malli.registry/registry (update-vals map-or-reg to-schema))))
  ([reg-a reg-b]
   (let [reg-a (registry reg-a)]
     (malli.registry/composite-registry ; composed backwards so right-value takes precedence in linear searches
       (if (malli.registry/registry? reg-b)
         reg-b
         (malli.registry/registry (update-vals reg-b (partial to-schema (registry reg-a)))))
       reg-a)))
  ([reg-a reg-b & maps-or-regs]
   (reduce registry (registry reg-a reg-b) maps-or-regs)
   ))

#_(let [regs (map (fn [reg] (if (malli.registry/registry? reg)
                              reg
                              (malli.registry/registry (update-vals reg to-schema))))
               maps-or-regs)]
    (if (> (count regs) 1)
      (apply malli.registry/composite-registry regs)
      (first regs)))

(comment
  (:db/isComponent _schema)
  (datomic->malli-schema {:db/isComponent (:db/isComponent _schema)})
  (registry (datomic->malli-schema {:db/isComponent (:db/isComponent _schema)}))
  (def is-comp-reg (datomic->malli-schema {:db/isComponent (:db/isComponent _schema)}))
  (registry is-comp-reg {:db/isComponent :boolean})
  )

(comment
  (registry)
  (registry {:foo :string})
  (registry {:foo :string} {:bar :foo})
  (malli.registry/schema (registry {:foo :string} {:bar :foo}) :bar)
  (malli.registry/schema (registry {:foo :string} {:bar :foo}) :foo)
  (malli.registry/schema (registry {:foo :string} {:bar :foo} {:baz :bar}) :baz)
  )

(defn schema [registry a] (malli.registry/schema registry a))
(defn schema-props [schema] (when schema (malli.core/properties schema)))
(defn cardinality [schema] (::cardinality (schema-props schema) ::one))
(defn schema-form [schema] (when schema (malli.core/form schema)))
(defn schema-type [schema]
  (or (::type (schema-props schema))
    (when-let [form (schema-form schema)]
      (cond (vector? form) (let [[type & _] form]
                             (if (= :schema type)
                               (schema-type (first (malli.core/children schema)))
                               type))
            (map? form)    (:type form)
            :else          form))))

(comment
  (schema-type (malli/schema [:sequential {:foo :bar} :string]))
  (malli.util/equals (malli/schema [:sequential {:foo :bar} :string]) [:sequential :string])

  (registry {:foo :string}
    {:bar [:ref :foo]})

  )

;;; Datomic

#?(:clj (def long? #(instance? java.lang.Long %)))
#?(:clj (def bigint? #(instance? clojure.lang.BigInt %)))
#?(:clj (def bigdec? #(instance? java.math.BigDecimal %)))

#?(:clj
   (def datomic-types {:db.type/bigdec  [:fn bigdec?]
                       :db.type/bigint  [:fn bigint?]
                       :db.type/boolean :boolean
                       :db.type/double  :double
                       :db.type/float   float?
                       :db.type/instant inst?
                       :db.type/keyword :keyword
                       :db.type/long    [:fn long?]
                       :db.type/ref     :any  ; no type for ref, would render as a link, not sure if :any is the right choice
                       :db.type/string  :string
                       :db.type/symbol  :symbol
                       ;; :db.type/tuple   :tuple ; TODO not supported yet
                       :db.type/uuid    :uuid
                       :db.type/uri     uri?
                       :db.type/bytes   bytes?
                       :db.type/fn      [:fn fn?]
                       }))

(defn db-ident [attribute schema]
  (let [attr (get schema attribute)]
    (if (map? attr)
      (:db/ident attr)
      attr)))

(def datomic-schema-valueType (partial db-ident :db/valueType))
(def datomic-schema-cardinality (partial db-ident :db/cardinality))

#?(:clj
   (def datomic-registry (registry malli.core/default-registry datomic-types)))

#?(:clj
   (defn datomic->malli-schema [schema]
     ;; (def _schema schema)
     (->> schema
       (vals)
       (map (fn [{:db/keys [ident cardinality] :as properties}]
              [ident
               (-> (malli.core/schema (datomic-schema-valueType properties) {:registry datomic-registry})
                 (malli.util/update-properties (constantly (assoc properties ::cardinality (case cardinality :db.cardinality/many ::many ::one)
                                                             ::type (datomic-schema-valueType properties)))))]))
       (into {})
       (registry datomic-registry))))

(comment
  (registry (datomic->malli-schema _schema) {:db/isComponent :boolean})
  (malli.core/schema (malli.registry/schema (datomic->malli-schema _schema) :db/valueType))
  (malli.core/form (malli.registry/schema (datomic->malli-schema _schema) :db/valueType))
  (get :db.type/fn _schema)
  (malli.util/merge [:string] [:int])
  )

(comment

  (schema-type (malli.core/schema [:sequential {:datagrid.schema/cardinality :datagrid.schema/many} :any]))
  (cardinality (malli.core/schema [:sequential {:datagrid.schema/cardinality :datagrid.schema/many} :any]))

  )
