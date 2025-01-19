(ns dustingetz.flatten-document)

(defn flatten-nested ; claude generated this
  ([data] (flatten-nested data []))
  ([data path]
   (cond
     (map? data)
     (mapcat (fn [[k v]]
               (cond
                 (or (map? v) (set? v))
                 (cons {:path path :name k} (flatten-nested v (conj path k)))

                 ; render collections of records as hyperlinks
                 (and (sequential? v) (map? (first v)))
                 [{:path path :name k :value (constantly v)}] ; lift

                 ; render simple collections inline
                 (and (sequential? v) (not (map? (first v))))
                 [{:path path :name k :value v}]

                 #_#_(nil? v) [{:path path :name k}]
                 () [{:path path :name k :value v}]))
       data)

     ; render simple collections as indexed maps
     (or (sequential? data) (set? data))
     (mapcat (fn [i v]
               (cond
                 (or (map? v) (sequential? v))
                 (cons {:path path :name i}
                   (flatten-nested v (conj path i)))

                 ()
                 [{:path path :name i :value v}]))
       (range) data)

     ; what else?
     () [{:path path :value data}])))

(comment
  (def test-data
    '{:response
     {:Owner
      {:DisplayName string
       :ID string}
      :Grants
      {:seq-of
       {:Grantee
        {:DisplayName string
         :EmailAddress string
         :ID string
         :Type [:one-of ["CanonicalUser" "AmazonCustomerByEmail" "Group"]]
         :URI string}
        :Permission [:one-of ["FULL_CONTROL" "WRITE" "WRITE_ACP" "READ" "READ_ACP"]]}}}})
  (flatten-nested test-data)
  )