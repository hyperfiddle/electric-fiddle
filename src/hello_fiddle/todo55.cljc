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

;; FIXME rotate such that `release!` is called after state is set.
(e/defn Transaction! [id F & args]
  (let [[[id args] release!] (el/DFlipFlop. [id args])]
    (e/with-cycle [state [::idle nil]]
      (if-not release!
        state ; fix point
        (try
          (release! [::accepted id (e/apply F args)]) ; (!) release! or state set could race
          (catch hyperfiddle.electric.Pending _
            [::pending id])
          (catch Cancelled c (throw c))
          (catch Throwable t
            (release! [::rejected id t])))))))

;; TODO simpler impl
#_
(e/defn Transaction! [id F & args]
  (let [[[id args] release!] (el/DFlipFlop. [id args])]
    (Filter. some?
      (when release! ; P: can the branch unmount before transfers are done?
        (try
          (release! [::accepted id (e/apply F args)]) ; (!) release! or return value could race
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

(e/def field-error nil)

;; What's the value of forwarding value and edit-fn? A: Cleaner API.
;; Experiment: move tx parallism to the MasterList
;; Ensure only the masterlist has a for-by.
;; The masterlist would create ##inf createnew inputs instead
;; and immediately apply the optimistic entity
;; while emitting collected dxs to the transactor.

(defn ->tx-id [stable-kf attribute]
  (let [next-tx-id (partial swap! (atom 0) inc)]
    (fn [[x dx]] [[(stable-kf x) attribute (next-tx-id)] x dx])))

;; Latch onto committed input value
;; Prioritize new user committed values over pending tx
;; careful state transitions to reflect TX progress
(e/defn Field [{::keys [#_attribute edit-fn #_stable-kf]} Body]
  (let [#_#_add-tx-id (->tx-id stable-kf attribute)]
    (::impulse
     (e/with-cycle [{::keys [status tx] :as state} {::status ::idle}]
       (prn {:field-state state})
       (let [[v Ack :as body] (Body. status)]
         (prn {:body body})
         ;; (prn "status" v Ack state)
         (case status
           (::idle ::accepted) {::status (if (and Ack v) ::dirty status)}
           ::dirty             (contrib.debug/dbg (Ack. {::status ::pending, ::tx #_(add-tx-id) (edit-fn v)}))
           (::pending ::rejected)
           (do (prn status {:body body})
               (if Ack
                 {::status ::dirty} ; a new value came in while pending, restart from initial state
                 (let [!next-state (atom nil)
                       [tx Ack-tx] (el/Pulse. tx)
                       Ack-tx      (e/fn [x] (when Ack-tx (Ack-tx. (when Ack (Ack. x)))) x)] ; when true bug workaround. FIXME Ack should not be truthy in this branch!
                   (if-some [next-state (e/watch !next-state)]
                     (if (= ::accepted (::status next-state))
                       (Ack-tx. next-state)
                       next-state)
                     {::status  status
                      ::tx      tx
                      ::impulse [tx (e/fn [& [status reason]]
                                      ;; (prn "ACK" status reason)
                                      (case status
                                        ::pending  nil
                                        ::accepted (reset! !next-state {::status ::accepted})
                                        ::rejected (reset! !next-state (assoc state ::status ::rejected, ::reason reason))))]}))))))))))

(defn optimistic [stable-kf xdxs authoritative-xs] ; could this be modeled as a Spine?
  (let [index (contrib.data/index-by (fn [x _index] (stable-kf x)) authoritative-xs)]
    (vals (reduce (fn [index [[x dxs] _Ack]]
                    ;; (prn "inside" txid x dxs)
                    (if (contains? index (stable-kf x))
                      (update index (stable-kf x) #(merge % x))
                      (assoc index (stable-kf x) x)))
            index xdxs))))

(e/defn Fork
  [[v Ack :as pulse]]
  (let [!branches (atom #{})] ; TODO use an ordered set
    (when (and v Ack)
      (swap! !branches conj (e/snapshot pulse)))
    (e/for-by first [[v Ack :as pulse] (e/watch !branches)]
      [v (e/fn [& args]
           (prn "fork ack" args)
           (let [{::keys [status] :as next} (e/apply Ack args)]
             (when (= ::accepted status)
               (swap! !branches disj pulse)
               nil)))])))

(e/defn MasterList [{::keys [stable-kf create-new-fn authoritative-xs CreateForm EditForm]}]
  (e/client
    (let [!xdxs         (atom ())
          optimistic-xs (optimistic #(stable-kf %1 %2) (e/watch !xdxs) authoritative-xs) ; FIXME not compatible with virtual scroll, authoritative-xs transfers entirely
          index (into {} (Fork.
                           (Field. {::edit-fn create-new-fn}
                             (e/fn [_]
                               (CreateForm.)))))
          ]
      (prn "index" index)
      (prn "optimistic" optimistic-xs)
      (reset! !xdxs
        (concat
          (seq index)
          (dom/ul
            (apply concat
              (e/for-by stable-kf [x (sort-by :todo/created-at (ignore-pendings optimistic-xs))] ; FIXME for-by on wrong peer
                                        ; NOTE v3 dynamic siting
                (EditForm. x))))
          )))))

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
                                       "Enter"  (when (#{::rejected ::dirty ::idle} status) (el/commit!)) ; FIXME remove ::idle state, not meaningful
                                       "Escape" (do (el/discard!) (.blur node) nil)
                                       (.. % -target -value)))
    ;; reset retry tx state on focus, so pressing enter commits again
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

(e/defn CreateNewInput [] ; Not a regular input, doesn't hold on value, do not care about tx accepted/rejected
  (e/client
    (dom/input
      (dom/props {:placeholder "What needs to be done?"})
      (el/Pulse.
        (el/Stage.
          (e/fn* []
            (binding [el/commit! (comp el/discard! el/commit!)] ; discard after commit (clear input on enter)
              (ClearOnSubmitBehavior. dom/node)
              (AtomicEditsBehavior. dom/node ::idle el/stage))))))))

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

(defn mempty ([] []) ([v] (if v [v] [])))


(e/defn App []
  (e/client
    (dom/div (dom/props {:class "todomvc"})
      (binding [stable-kf (StableKf.)]
        #_(e/server)
        (MasterList.
          {::authoritative-xs (e/server (query-todos db)) ; FIXME should not transfer, waiting for v3
           ::stable-kf        stable-kf
           ::create-new-fn    (fn [v]
                                (genesis
                                  (fn [tempid] {:db/id           tempid
                                                :todo/text       v,
                                                :todo/created-at (inst-ms (js/Date.))})
                                  (fn [tempid]
                                    (cond->
                                        [[:db/add tempid, :todo/text v]
                                         [:db/add tempid, :todo/created-at (inst-ms (js/Date.))]]
                                      (= "exercise" v) (cons [])))))
           ::CreateForm       CreateNewInput
           ::EditForm
           (e/fn [x]
             (e/client ; NOTE v3 dynamic siting
               (dom/li
                 (e/client (dom/props {:title (pr-str x)}))
                 (concat ; e/amb emulation
                   (mempty
                     (Field. {::edit-fn (partial todo-edit-done stable-kf ((capture) x))} ; FIXME abstract over `capture` ; FIXME v2 compiler bug when inlined: Cannot set properties of undefined (setting '3')
                       (e/fn [status] ; D : could be called
                         (Checkbox. {::status status ::value (:todo/checked x false)} (e/fn* [] #_(dom/props ...))))))
                   (mempty
                     (Field. {::edit-fn (partial todo-edit-text stable-kf ((capture) x))}
                       (e/fn [status]
                         (Input. {::status status ::value (:todo/text x)} (e/fn* [] (dom/props {:type :text}))))))))))})))))

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
  `(e/for-by first [[[x# dx#] Ack# :as xdx#] (ignore-pendings (filter some? ~xdxs))]
     ;; (prn "Transactor" txid# x# dx# Ack#)
     (let [[status# reason#] (e/server (let [[status# _txid# tx-report#] (Transact!. ~conn dx#)]
                                         (case (ignore-pendings status#)
                                           ::accepted (do (reset! ~!tx-report tx-report#) [status# nil])
                                           ::rejected [status# (ex-message tx-report#)]
                                           [status# nil])))]
       (prn "tx result" status# x#)
       (new Ack# status# reason#))))

(e/defn StagingArea [Body]
  (e/client
    (el/Pulse.
      (el/Stage.
        (e/fn []
          (let [v (Body.)]
            (dom/div
              (dom/textarea (dom/text (contrib.str/pprint-str el/stage)))
              (dom/br)
              (dom/button (dom/text "commit") (el/EventListener. "click" #(do (el/commit!) (el/discard!))))
              (dom/button (dom/text "discard" (el/EventListener. "click" #(el/discard!)))))))))))

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
              (let [xdxs (dom/div (dom/props {:class "todomvc-wrapper"})   ; xdxs :: collection of tuples [[txid x dx] Ack]
                                  (App.))]
                (Transactor !tx-report conn xdxs)
                (e/client
                  (dom/div
                    (dom/pre (dom/props {:style {:align-self :start}}) (dom/text (contrib.str/pprint-str xdxs)))
                    (dom/br)
                    (dom/button (dom/text "commit"))
                    (dom/button (dom/text "discard")))
                  #_(dom/img (dom/props {:class "legend" :src "/hello_fiddle/state_machine.svg"}))
                  (hello-fiddle.todo-style/Style.)
                  nil)))))))))
