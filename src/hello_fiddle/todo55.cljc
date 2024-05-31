;; Next up:
;; - Add Form abstraction - only meaningful as a stage and for validation
;; - Figure out Form/Field/Input's API
;; - Figure out how to cancel pending txs — if even needed
;; - Remove e/for-by from Field. Only the masterlist and transactor should have a for-by

(ns hello-fiddle.todo55
  (:require
   [hyperfiddle.electric :as e]
   [hyperfiddle.electric-dom2 :as dom]
   [hello-fiddle.todo-style]
   [datascript.core :as d]
   [missionary.core :as m]
   [hello-fiddle.electronics :as el])
  (:import
   (missionary Cancelled)))

(e/defn Transaction! [id F & args]
  (let [[[id args] release!] (el/AutoLatch. [id args])]
    (e/with-cycle [state [::idle nil]]
      (if-not release!
        state
        (try
          (release! [::accepted id (e/apply F args)])
          (catch hyperfiddle.electric.Pending _
            [::pending id])
          (catch Cancelled c (throw c))
          (catch Throwable t
            (release! [::rejected id t])))))))

(e/defn Transact!* [conn tx tx-meta]
  (e/server
    (when tx
      (e/offload-task #(do (Thread/sleep 3000)
                           (d/transact! conn tx tx-meta))))))

(e/defn Transact!
  ([conn tx] (Transact!. conn tx nil))
  ([conn tx tx-meta]
   (Transaction!. (hash tx) Transact!* conn tx tx-meta)))

(defn capture
  "Captures variablity under a thunk with stable identity.
  Return a function taking any value and always returning a stable reference thunk.
  Calling this thunk returns the given value.
  Use case: prevent unmount and remount when a cc/fn argument updates due to an inner variable dependency."
  []
  (let [!state (object-array 1)
        ret #(aget !state 0)]
    (fn [x]
      (aset !state 0 x)
      ret)))

(defmacro ignore-pendings [x]
  `(let [!x# (atom (e/snapshot ~x))]
     (try (reset! !x# ~x) (catch hyperfiddle.electric.Pending ~'_))
     (e/watch !x#)))

(e/defn TxUI [status]
  (e/client
    (dom/props {:class (case status (nil ::idle) nil, (name status))})))

;;; ------------

(defn query-todos [db] (d/q '[:find [(pull ?e [*]) ...] :where (or [?e :todo/text] [?e :todo/checked])] db))

(e/def conn nil)
(e/def db nil)
(e/def !tx-report (atom {})) ; (atom {:tempids {}})
(e/def tx-report {})

(defn tempid? [x] (and (number? x) (neg? x)))

(e/defn StableKf []
  (let [!tempids (atom {})]
    (reset! !tempids (e/server (::revids tx-report)))
    (fn [entity]
      (if (tempid? (:db/id entity))
        (:db/id entity)
        (get @!tempids (:db/id entity) (:db/id entity))))))


(e/def stable-kf :db/id)

;; How do we stabilize tempid to db/id?
;; 1. Store tempid->real_id map
;;    Stabilize rows on the original tempid until tab refresh
;;    Don't store tempids in the entity. Store it in some dynamically bound reverse store

(defn uid [] (Math/abs (hash (random-uuid))))

(defn genesis [x! dx!]
  (let [tempid (- (uid))]
    ((juxt x! dx!) tempid)))

(defn field-identifier [[e a _id :as tx-id]]
  [e a])

(e/defn TxEmitter [tx-parallelism]
  (let [!txs (atom ())] ; Experiment: unbundle and return atom in xdxs
    [(e/watch !txs)
     (fn emit! [tx] (swap! !txs #(cons tx (take (dec tx-parallelism) %))) nil)
     (fn retract! [[e a _id]] (swap! !txs (partial remove (fn [[tx-id _x _dx]]
                                                            (= [e a] (field-identifier tx-id)))))
       nil)]))

(e/defn Drop [n value]
  (e/client
    (new (m/relieve {} (m/reductions {} nil (m/eduction (drop n) (e/fn* [] value)))))))

(e/defn TxMonitor [stable-kf txid eid] ; Monitor dx succes for x.
  (e/client
    (let [tx-report                      (e/server (select-keys tx-report ; ignore current tx-report, only look at next one
                                                     [::status
                                                      ::last-x
                                                      ::last-txid ; selected to skip deduplication and ensure network transfer
                                                      ::error]))
          {::keys [status last-x last-txid error]} (Drop. 2 tx-report)] ; drop 2 instead of 1 due to runaway pendings
      (when (and (= eid (stable-kf last-x))
              (or (nil? txid)
                (= (field-identifier txid) (field-identifier last-txid))))
        (case status
          ::accepted [::accepted]
          ::rejected [::rejected error]
          nil)))))

(e/def field-error nil)

;; What's the value of forwarding value and edit-fn? A: Cleaner API.
;; Experiment: move tx parallism to the MasterList
;; Ensure only the masterlist has a for-by.
;; The masterlist would create ##inf createnew inputs instead
;; and immediately apply the optimistic entity
;; while emitting collected dxs to the transactor.

#_(e/defn Field [{::keys [attribute stable-kf eid value edit-fn tx-parallelism]
                :or    {tx-parallelism 1}}
               Body]
  (let [tx-id                 (partial swap! (atom 0) inc)
        !status               (atom ::idle)
        [xdxs emit! retract!] (TxEmitter. tx-parallelism)
        emit!                 (fn [[x dx :as xdx]] (emit! (vec (cons [(stable-kf x) attribute (tx-id)] xdx))))
        !error                (atom nil)]
    (binding [field-error (e/watch !error)]
      (when eid
        (reset! !status ::pending)
        (let [[status error] (TxMonitor. stable-kf nil eid)]
          (case status
            ::accepted (do (reset! !status ::accepted) (reset! !error nil))
            ::rejected (do (reset! !status ::rejected) (reset! !error error))
            nil)))
      ((fn [xdxs] (when (not-empty xdxs) (reset! !status ::pending))) xdxs)
      (e/for-by identity [[txid x dx :as xdx] xdxs]
        (let [[status error] (ignore-pendings (TxMonitor. stable-kf txid (stable-kf x)))]
          (case status
            ::accepted (do (retract! txid) (reset! !status ::accepted) (reset! !error nil))
            ::rejected (do #_(retract! xdx) (reset! !status ::rejected) (reset! !error error))
            nil)))
      (let [[v Ack] (Body. value (e/watch !status))]
        (when Ack
          (Ack. (emit! (edit-fn v)))))   ; edit-fn must be stable!
      (when field-error
        (e/client
          (dom/span
            (dom/props {:class "field-error"})
            (dom/text "Failed to persist " attribute)
            #_(dom/text field-error))))
      xdxs)))

(defn ->tx-id [stable-kf attribute]
  (let [next-tx-id (partial swap! (atom 0) inc)]
    (fn [[x dx]] [[(stable-kf x) attribute (next-tx-id)] x dx])))

(e/defn Field [{::keys [attribute value edit-fn stable-kf]} Body]
  (let [add-tx-id (->tx-id stable-kf attribute)]
    (doto
        (::impulse
         (e/with-cycle [{::keys [status tx] :as state} {::status ::idle}]
           (prn attribute state)
           (let [[v Ack] (Body. value status)]
             (case status
               (::idle ::accepted)  {::status (if (and Ack v) ::dirty status)}
               ::dirty (Ack. {::status ::pending, ::tx (add-tx-id (edit-fn v))})
               (::pending ::rejected)
               (if Ack
                 {::status ::dirty} ; a new value came in while pending, restart from initial state
                 (let [!next-state (atom nil)
                       [tx Ack-tx] (el/Pulse. tx)
                       Ack-tx      (e/fn [x] (when Ack-tx (Ack-tx. (when Ack (Ack. x)))) x)] ; when true bug workaround
                   (if-some [next-state (e/watch !next-state)]
                     (if (= ::accepted (::status next-state))
                       (Ack-tx. next-state)
                       next-state)
                     {::status  status
                      ::tx      tx
                      ::impulse [[tx (e/fn [& [status reason]]
                                       (prn "ACK" status reason)
                                       (case status
                                         ::pending  nil
                                         ::accepted (reset! !next-state {::status ::accepted})
                                         ::rejected (reset! !next-state (assoc state ::status ::rejected, ::reason reason))))]]})))))))
      (prn 'impulse))))

(defn optimistic [stable-kf xdxs authoritative-xs] ; could this be modeled as a Spine?
  (let [index (contrib.data/index-by (fn [x _index] (stable-kf x)) authoritative-xs)]
    (vals (reduce (fn [index [[txid x dxs] _Ack]]
                    ;; (prn "inside" txid x dxs)
                    (if (contains? index (stable-kf x))
                      (update index (stable-kf x) #(merge % x))
                      (assoc index (stable-kf x) x)))
            index xdxs))))

(e/defn MasterList [{::keys [authoritative-xs CreateForm EditForm]}]
  (e/client
    (let [!xdxs         (atom ())
          optimistic-xs (optimistic #(stable-kf %1 %2) (e/watch !xdxs) authoritative-xs) ; FIXME not compatible with virtual scroll, authoritative-xs transfers entirely
          ]
      (reset! !xdxs
        (concat
          (CreateForm.)
          (dom/ul
            (apply concat
              (e/for-by stable-kf [x (sort-by :todo/created-at (ignore-pendings optimistic-xs))] ; FIXME for-by on wrong peer
                                        ; NOTE v3 dynamic siting
                (EditForm. x)))))))))

(e/defn CommitOnBlurBehavior "Commit current stage when given `node` is blured.
An input can be blurred e.g. by clicking outside or pressing Tab."
  [node status]
  (when (= ::dirty status)
    (el/EventListener. node "blur" #(el/commit!))))

(e/defn AtomicInputEditsBehavior
  "Augment a dom input so it accumulate edits and:
  - emits the latest one on Enter pressed (submit)
  - cancel the edits and resets the input to the latest authoritative value (discard)
   The input content will reflect the authoritative value unless:
   - user has focused the input,
   - user has staged edits."
  [node status value]
  (set! (.-value node) (or el/stage value))
  (TxUI. status) ; Tag control with status as CSS class
  (or
    ;; stage typed text, emit (commit) on Enter pressed / discard on Escape
    (el/EventListener. node "keyup" #(case (.-key %)
                                       "Enter"  (when (#{::rejected ::dirty} status) (el/commit!))
                                       "Escape" (do (el/discard!) (.blur node) nil)
                                       (.. % -target -value)))
    ;; reset retry tx state on focus
    (when (= ::rejected status)
      (el/EventListener. node "focus" #(.. % -target -value)))))

(e/defn CheckboxBehavior
  "A checkbox is a special kind of input with extra UX constraints:
   - binary state (checked or not checked) – no dirty state, user cannot loose dirty edits.
     - no need to handle blur, as state
   - submit is performed with mouse click or Space key
   - Escape blurs, but doesn't mean discard, as there is no dirty state.
   - focus is only meaningful with keyboard navigation (tab)
     - local optimistic state doesn't prevail over authoritative state
       For UX reasons, a checkbox should transition ASAP to the latest authoritative state
       A checkbox should never wait user interaction to reset -> would damage user info flow.
       This is what spreadsheet do.
   - TODO DOM checkboxes have indeterminate state - e.g. for tree-like select-multi UIs
     Not sure if this abstraction should handle this indeterminate state.
     Probably leave control to user for custom UX."
  [node status value]
  (set! (.-checked node)
    (case status
      ::rejected (not value) ; reset checkbox state so user can retry
      value))
  (TxUI. status)
  ;; autocommit checked state
  (el/EventListener. node "change" #(el/commit! (.. % -target -checked))))

#?(:cljs
   (defn watch-attributes [node html-attributes]
     (let [html-attributes (set html-attributes)]
       (m/relieve {}
         (m/reductions into (into {} (map (juxt identity #(.getAttribute node %)) html-attributes))
           (m/observe
             (fn [!]
               (let [observer (js/MutationObserver. (fn [mutation-list _observer]
                                                     (! (filter (comp html-attributes first)
                                                          (map (fn [mutation]
                                                                 (let [attrName (.-attributeName mutation)]
                                                                   [attrName (.getAttribute node attrName)]))
                                                            mutation-list)))))]
                 (.observe observer node #js{:attributes true})
                 #(.disconnect observer)))))))))

(e/defn Attributes [node html-attribute-names]
  (e/client (new (watch-attributes node html-attribute-names))))

(e/defn AtomicEditsBehavior ; D: Used to be called HyperControl?
  "Augment a dom input so it accumulate edits and:
  - emits the latest one on Enter pressed (submit)
  - cancel the edits and resets the input to the latest authoritative value (discard)
   The input content will reflect the authoritative value unless:
   - user has focused the input,
   - user has staged edits."
  [node status value]
  (case (get (Attributes. node #{"type"}) "type")
    "checkbox" (CheckboxBehavior. node status value)
    "submit"   nil ; TODO ; autocommit because button (binary state)
    "button"   nil ; TODO ; ! in a form always use <button type="button">, cause submit is the default
    (nil "text")     (AtomicInputEditsBehavior. node status value)
    (AtomicInputEditsBehavior. node status value)))

(e/defn ClearOnSubmitBehavior
  "Clear an input on submit. A common pattern to chat interfaces and todo-apps"
  [node]
  (when (empty? el/stage)
    (set! (.-value node) nil)))

(e/defn SpreadSheetTextInputBehavior ; TODO rename? SpreadSheet TEXT input cell
  "Augment a dom input to make it behave like a spreadsheet text input.
   The input will accumulate changes until:
   - Enter, Tab is pressed or user click outside , emitting the value.
   - Escape is pressed, rolling back the value to the latest authoritative one.
   The input content will reflect the authoritative value unless:
   - user has focused the input,
   - user has staged edits."
  [node status value]
  (let [status (if (and (some? el/stage) (not= el/stage value)) ::dirty status)]
    (CommitOnBlurBehavior. node status)
    (AtomicEditsBehavior. node status value)))

(e/defn Input [{::keys [status value]} Body]
  (e/client
    (dom/input
      (Body.)
      (el/Pulse.
        (el/Stage.
          (e/fn []
            (SpreadSheetTextInputBehavior. dom/node status value)))))))

(e/defn CreateNewInput [value status] ; Not a regular input, doesn't hold on value, do not care about tx accepted/rejected
  (e/client
    (dom/input
      (dom/props {:placeholder "What needs to be done?"})
      (el/Pulse.
        (el/Stage.
          (e/fn []
            (binding [el/commit! (comp el/discard! el/commit!)] ; discard after commit (clear input on enter)
              (ClearOnSubmitBehavior. dom/node)
              (AtomicEditsBehavior. dom/node
                (if (and (some? el/stage) (not= el/stage value)) ::dirty status)
                value))))))))

(e/defn Checkbox [{::keys [status value]} Body]
  (e/client
    (dom/input
      (dom/props {:type :checkbox})
      (when (= ::pending status) (dom/props {:disabled true}))
      (Body.)
      (el/Pulse.
        (el/Stage.
          (e/fn []
            (AtomicEditsBehavior. dom/node status value)))))))

(defn to-dx [x]
  (map (fn [[k v]] [:db/add (:db/id x) k v]) (dissoc x :db/id)))

(defn best-identity
  "TODO wire up schema
  Return the best identity for a given entity and schema.
  (e.g. :db/id, :user-domain/id)"
  ([] (best-identity nil nil))
  ([_schema _entity] :db/id)) ; stub

(defn best-identity-value
  "TODO wire up schema
  Return the best identity's value for a given entity and schema.
  (e.g. 42, [:lookup/ref 42])"
  ([entity] (best-identity-value nil entity)) ; stub
  ([schema entity] (get entity (best-identity schema entity)))) ; stub

(defn base
  ([entity] (base nil entity)) ; stub
  ([schema entity] (select-keys entity [(best-identity schema entity)])))

(defn todo-edit-done [stable-kf get-x v]
  (let [x (get-x)
        x+dx (assoc (base x) :todo/checked v)] ; Only alter one attr, or get races between fields of same entity FIXME
    [x+dx
     (if (tempid? (stable-kf x))
       (to-dx (assoc x :todo/checked v))
       [[:db/add (best-identity-value x) :todo/checked v]])]))

(defn todo-edit-text [stable-kf get-x v]
  (let [x    (get-x)
        x+dx (assoc (base x) :todo/text v)] ; Only alter one attr, or get races between fields of same entity FIXME
    [x+dx
     (if (tempid? (stable-kf x))
       (to-dx (assoc x :todo/text v))
       [[:db/add (best-identity-value x) :todo/text v]])]))

(e/defn App []
  (e/client
    (dom/div (dom/props {:class "todomvc"})
      (binding [stable-kf (StableKf.)]
        #_(e/server)
        (MasterList.
          {::authoritative-xs (e/server (query-todos db)) ; FIXME should not transfer, waiting for v3
           ::CreateForm
           (e/fn []
             (e/client ; NOTE v3 dynamic siting
               (Field. {::attribute      :todo/text
                        ::stable-kf      stable-kf
                        ::value          nil
                        ::edit-fn        (fn [v]
                                           (genesis
                                             (fn [tempid] {:db/id tempid
                                                           :todo/text v,
                                                           :todo/created-at (inst-ms (js/Date.))})
                                             (fn [tempid]
                                               (cond->
                                                   [[:db/add tempid, :todo/text v]
                                                    [:db/add tempid, :todo/created-at (inst-ms (js/Date.))]]
                                                 (= "exercise" v) (cons [])))))
                        ::tx-parallelism ##Inf}
                 CreateNewInput)))
           ::EditForm
           (e/fn [x]
             (e/client ; NOTE v3 dynamic siting
               (dom/li
                 (e/client (dom/props {:title (pr-str x)}))
                 (concat
                   (Field. {::attribute :todo/checked
                            ::stable-kf stable-kf
                            ::value     (:todo/checked x false)
                            ::edit-fn   (partial todo-edit-done stable-kf ((capture) x))} ; FIXME abstract over `capture` ; FIXME v2 compiler bug when inlined: Cannot set properties of undefined (setting '3')
                     (e/fn [value status]
                       (Checkbox. {::status status ::value value} (e/fn* [] #_(dom/props ...)))))
                   (Field. {::attribute :todo/text
                            ::stable-kf stable-kf
                            ::eid       (when (tempid? (stable-kf x)) (stable-kf x))
                            ::value     (:todo/text x)
                            ::edit-fn   (partial todo-edit-text stable-kf ((capture) x))}
                     (e/fn [value status]
                       (let [v (Input. {::status status ::value value} (e/fn* [] (dom/props {:type :text})))
                             retry-v nil #_(when field-error
                                             (dom/div (dom/props {:class "retry-container"})
                                                      (dom/button
                                                        (dom/text "retry")
                                                        (TxUI. status)
                                                        (when-let [[event !done running?] (new (listen dom/node "click"))]
                                                          (!done)
                                                          value))))]
                         (or retry-v v))))
                   ))))})))))

(defn rev-ids [report]
  (let [tempids (dissoc (:tempids report) :db/current-tx)]
    (zipmap (vals tempids) (keys tempids))))

(defn merge-tx-reports
  ([] {})
  ([report] (if-not (contains? report ::revids)
              (assoc report ::revids (rev-ids report))
              report))
  ([report1 report2]
   (-> (merge (merge-tx-reports report1) (into {} report2))
     (update ::revids merge (rev-ids report2)))))

;; 3 types of TX errors
;; - Transaction content rejected
;; - Some system part is failing and tx should be retained and retried
;; - Transaction rejected but the conflict has been resolved elsewhere
;;   and tx will be accepted next time
;; Is it the error due to the tx data at point in time or to the machine being
;; broken?
;; We want to avoid tx to be discrete

#_
(e/defn Transactor [!tx-report conn xdxs]
  (e/server
    (e/for-by first [[txid x dx] (ignore-pendings (filter some? xdxs))]
      (let [[status _txid tx-report] (Transact!. conn dx)]
        (swap! !tx-report merge-tx-reports
          (case (ignore-pendings status)
            ::accepted (assoc tx-report ::last-x x, ::last-txid txid, ::status ::accepted)
            ::rejected {::last-x x, ::last-txid txid ::status ::rejected ::error (ex-message tx-report)}
            nil))
        nil))
    nil))

(defmacro Transactor [!tx-report conn xdxs] ; should not be a macro, blocked on v3
  `(e/for-by first [[[txid# x# dx#] Ack# :as xdx#] (ignore-pendings (filter some? ~xdxs))]
     ;; (prn "Transactor" txid# x# dx# Ack#)
     (let [[status# reason#] (e/server (let [[status# _txid# tx-report#] (Transact!. ~conn dx#)]
                                         (case (ignore-pendings status#)
                                           ::accepted (do (reset! ~!tx-report tx-report#) [status# nil])
                                           ::rejected [status# (ex-message tx-report#)]
                                           [status# nil])
                                         ))]
       (new Ack# status# reason#))))

(e/defn Todo5 []
  (e/server
    (binding [conn (d/create-conn)]
      (binding [!tx-report (atom (d/transact! conn [#_{:todo/text "Hello", :todo/created-at (inst-ms (java.util.Date.))}
                                                    #_{:todo/text "world", :todo/created-at (inc (inst-ms (java.util.Date.)))}]))]
        (def repl-conn conn)
        (def repl-transact! #(reset! !tx-report (d/transact! conn %)))
        (binding [tx-report (new (m/stream (m/watch !tx-report)))] ; ensures all dependants sees individual tx-reports
          (binding [db (:db-after tx-report)]
            (e/client (dom/h1 (dom/text "todos")))
            (e/client ; FIXME should be server-side
              (let [xdxs (App.)]      ; xdxs :: collection of tuples [txid x dx]
                (Transactor !tx-report conn xdxs)
                (e/client
                  (dom/pre (dom/props {:style {:align-self :start}}) (dom/text (contrib.str/pprint-str xdxs)))
                  (dom/img (dom/props {:class "legend" :src "/hello_fiddle/state_machine.svg"}))
                  (hello-fiddle.todo-style/Style.)
                  nil)))))))))
