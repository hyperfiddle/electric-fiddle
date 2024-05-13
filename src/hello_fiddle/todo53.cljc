;; Next up:
;; - Figure out how to correlate tx between create new and optimistic row
;; - Add Form abstraction
;; - Figure out Form/Field/Input's API
;; - Figure out how to cancel pending txs — if even needed

(ns hello-fiddle.todo53
  (:require
   [hyperfiddle.electric :as e]
   [hyperfiddle.electric-dom2 :as dom]
   [hello-fiddle.todo-style]
   [datascript.core :as d]
   [missionary.core :as m]
   [hello-fiddle.stage :as stage])
  (:import
   (missionary Cancelled)))

(defn listen
  ([node event] (listen node event identity))
  ([node event cb]
   (m/relieve {}
     (m/reductions {} nil
       (m/observe (fn [!]
                    (let [!open? (volatile! true)
                          f     (fn [e] (when @!open?
                                          (when-some [e (cb e)]
                                            (vreset! !open? false)
                                            (! [e
                                                (fn [& _]
                                                  (vreset! !open? true)
                                                  (! [e (constantly nil) false])
                                                  nil)
                                                true]))))]
                      (.addEventListener node event f)
                      #(.removeEventListener node event f))))))))

(e/defn Transaction! [id F & args]
  (let [!last-state (atom [::idle nil])
        !run?       (atom false)
        run?        (e/watch !run?)]
    ((fn [_] (when-not @!run? (reset! !run? true))) args)
    (when run?
      (let [id (e/snapshot id)
            args (e/snapshot args)]
        (try
          ((fn [_] (reset! !run? false))
           (reset! !last-state [::success id (e/apply F args)]))
          (catch hyperfiddle.electric.Pending _
            (reset! !last-state [::pending id]))
          (catch Cancelled c (throw c))
          (catch Throwable t
            ((fn [& _] (reset! !run? false))
             t
             (reset! !last-state [::failure id t]))))))
    (e/watch !last-state)))

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
    (case (ignore-pendings status)
      (nil ::idle) nil
      ::dirty      (dom/props {:class "dirty"})
      ::pending    (dom/props {:class "pending"})
      ::accepted   (dom/props {:class "success"})
      ::rejected   (dom/props {:class "failure"}))
    status))


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

(defn genesis [x! dx!] ;; TODO looks like x! is unused. Why? Is dx! enough to model genesis? x₀ = id ⊕ dx₀ = dx₀ ?
  (let [tempid (- (uid))]
    [(x! tempid) (dx! tempid)]))

(e/defn TxEmitter [tx-parallelism]
  (let [!txs (atom ())] ; Experiment: unbundle and return atom in xdxs
    [(e/watch !txs)
     (fn emit! [tx] (swap! !txs #(cons tx (take (dec tx-parallelism) %))) nil)
     (fn retract! [tx] (swap! !txs (partial remove #{tx})) nil)]))

(e/defn Drop [n value]
  (e/client
    (new (m/relieve {} (m/reductions {} nil (m/eduction (drop n) (e/fn* [] value)))))))

(e/defn TxMonitor [stable-kf eid] ; Monitor dx succes for x. ; TODO could two = txs race?
  (e/client
    (let [tx-report                      (ignore-pendings (e/server (select-keys tx-report ; ignore current tx-report, only look at next one
                                                  [::status ::last-x ::error])))
          {::keys [status last-x error]} (Drop. 2 tx-report)]
      (when (= eid (stable-kf last-x))
        (case status
          ::accepted [::accepted]
          ::rejected [::rejected error]
          nil)))))

(defn optimistic [stable-kf xdxs authoritative-xs] ; could this be modeled as a Spine?
  (let [index (contrib.data/index-by (fn [x _index] (stable-kf x)) authoritative-xs)]
    (vals (reduce (fn [index [txid x dxs]]
                    (if (contains? index (stable-kf x))
                      (update index (stable-kf x) #(merge % x))
                      (assoc index (stable-kf x) x)))
            index xdxs))))

(e/def field-error nil)

;; What's the value of forwarding value and edit-fn? A: Cleaner API.
;; Experiment: move tx parallism to the MasterList
;; Ensure only the masterlist has a for-by.
;; The masterlist would create ##inf createnew inputs instead
;; and immediately apply the optimistic entity
;; while emitting collected dxs to the transactor.
(e/defn Field [{::keys [stable-kf eid value edit-fn tx-parallelism]
                :or    {tx-parallelism 1}}
               Body]
  (let [tx-id                 (partial swap! (atom 0) inc)
        !status               (atom ::idle)
        [xdxs emit! retract!] (TxEmitter. tx-parallelism)
        emit!                 (fn [[x dx :as xdx]] (emit! (vec (cons (str (stable-kf x) "_" (tx-id)) xdx))))
        !error                (atom nil)]
    (binding [field-error (e/watch !error)]
      (when eid
        (reset! !status ::pending)
        (let [[status error] (TxMonitor. stable-kf eid)]
          ;; (prn "optimistic status" status)
          (case status
            ::accepted (do (reset! !status ::accepted) (reset! !error nil))
            ::rejected (do (reset! !status ::rejected) (reset! !error error))
            nil)))
      ((fn [xdxs] (when (not-empty xdxs) (reset! !status ::pending))) xdxs)
      (e/for-by identity [[txid x dx :as xdx] xdxs]
        (let [[status error] (ignore-pendings (TxMonitor. stable-kf (stable-kf x)))]
          ;; (prn (stable-kf x) status)
          (case status
            ::accepted (do (retract! xdx) (reset! !status ::accepted) (reset! !error nil))
            ::rejected (do #_(retract! xdx) (reset! !status ::rejected) (reset! !error error))
            nil)))
      (when-some [v (Body. value (e/watch !status))]
        ((e/snapshot (fn [v] (emit! (edit-fn v)))) v))
      xdxs)))

(e/defn MasterList [{::keys [authoritative-xs CreateForm EditForm]}]
  (e/client
    (let [!xdxs         (atom ())
          optimistic-xs (optimistic #(stable-kf %1 %2) (e/watch !xdxs) authoritative-xs); FIXME not compatible with virtual scroll, authoritative-xs transfers entirely
          ]
      (reset! !xdxs
        (concat
          (e/server  ; TODO v3 dynamic siting
            (CreateForm.))
          (dom/ul
            (apply concat
              (e/for-by stable-kf [x (sort-by :todo/created-at (ignore-pendings optimistic-xs))] ; FIXME stabilize ; FIXME for-by on wrong peer
                ;; (prn "x" x)
                (e/server  ; TODO v3 dynamic siting
                  (EditForm. x))))))))))

(e/defn CommitOnBlurBehavior "Commit current stage when given `node` is blured.
An input can be blurred e.g. by clicking outside or pressing Tab."
  [node]
  (when-let [[_event done! running?] (new (listen node "blur"))]
    (when running?
      (case (stage/Commit.) ; assumed called in a stage
        (done!)))))

(e/defn AtomicInputEditsBehavior
  "Augment a dom input so it accumulate edits and:
  - emits the latest one on Enter pressed (submit)
  - cancel the edits and resets the input to the latest authoritative value (discard)
   The input content will reflect the authoritative value unless:
   - user has focused the input,
   - user has staged edits."
  [node status value]
  (let [!last-stage (atom nil)]
    ;; stage typed text
    (when-let [[value' done! _running?] (new (listen node "input" #(.. % -target -value)))]
      (stage/stage! value')
      (reset! !last-stage value')
      (done!))
    ;; reset retry tx state on focus
    (when-let [[_event done! running?] (new (listen node "focus"))]
      (when running?
        (reset! !last-stage value)
        (done!)))
    ;; Allow user to press enter again to retry tx
    (when (and (nil? stage/stage) (= ::rejected status))
      (stage/Commit.)) ; emit nil WIP
    ;; Emit (commit) on Enter pressed / discard on Escape
    (when-let [[event done! running?] (new (listen node "keyup"))]
      (when running?
        (case (.-key event)
          "Enter"  (case status
                     ::rejected (stage/Commit. @!last-stage)
                     (do #_(prn "stage/stage" stage/stage)
                         (case (stage/Commit.)
                           (done!))))
          "Escape" (case (do (stage/discard!) (.blur node))
                     (done!))
          (done!)))))
  (let [value  (or stage/stage value) ; don't damage user uncommitted typing
        status (cond (some? stage/stage) ::dirty
                     :else               status)]
    (TxUI. status) ; Render 4 colors ; FIXME misplaced, too low level here
    (when-not (dom/Focused?.) ; don't alter input while user-focused (UX)
      (set! (.-value node) value))))

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
  (when (and (nil? stage/stage) (= ::rejected status)) ; retry tx
    (stage/Commit.))
  (TxUI. status)
  ;; autocommit checked state
  (when-let [[value' done! running?] (new (listen node "change" #(.. % -target -checked)))]
    (when running?
      (stage/stage! value')
      (case (stage/Commit.)
        (done!)))))

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
  ;; (e/client (js/console.log "input type" node (get (Attributes. node #{"type"}) "type")))
  (case (get (Attributes. node #{"type"}) "type")
    "checkbox" (CheckboxBehavior. node status value)
    "submit"   nil ; TODO ; autocommit because button (binary state)
    "button"   nil ; TODO ; ! in a form always use <button type="button">, cause submit is the default
    (nil "text")     (AtomicInputEditsBehavior. node status value)
    (AtomicInputEditsBehavior. node status value)))

(e/defn ClearOnSubmitBehavior
  "Clear an input on submit. A common pattern to chat interfaces and todo-apps"
  [node]
  ;; (prn "ClearOnSubmitBehavior" stage/stage)
  (when (empty? stage/stage)
    #_(when (not-empty ((fn [_] (.-value node)) stage/stage)) ; not composed in a single (and …) so expr reruns
)
    (set! (.-value node) nil)))

(e/defn SpreadSheetCellBehavior ; TODO rename? SpreadSheet TEXT input cell
  "Augment a dom input to make it behave like a spreadsheet cell.
   The input will accumulate changes until:
   - Enter, Tab is pressed or user click outside , emitting the value.
   - Escape is pressed, rolling back the value to the latest authoritative one.
   The input content will reflect the authoritative value unless:
   - user has focused the input,
   - user has staged edits."
  [node status value]
  (AtomicEditsBehavior. node status value)
  (CommitOnBlurBehavior. node))

(e/defn WithStage
  "Run `Body` in a `stage` and return latest committed value."
  [Body]
  (let [!value (atom nil)]
    (stage/staged (e/fn* [value] (reset! !value value))
      (Body.))
    (new (m/stream (m/watch !value)))))

(defmacro with-stage
  "Run `Body` in a `stage` and return latest committed value."
  [& body]
  `(new WithStage (e/fn* [] ~@body)))

(e/defn Input [{::keys [status value]} Body]
  (e/client
    (let [value (dom/input
                  (Body.)
                  (with-stage (SpreadSheetCellBehavior. dom/node status value)))]
      (when field-error
        (dom/span (dom/props {:class "error"}) (dom/text field-error)))
      value)))

(e/defn CreateNewInput [value status] ; Not a regular input, doesn't hold on value, do not care about tx success/failure
  (e/client
    (dom/input
      (dom/props {:placeholder "What needs to be done?"})
      (with-stage
        (AtomicEditsBehavior. dom/node status value)
        (ClearOnSubmitBehavior. dom/node)))))

;; FIXME odd behavior when failure. Seems to retry forever until settled
(e/defn Checkbox [{::keys [status value]} Body]
  (e/client
    (dom/input
      (dom/props {:type :checkbox})
      (when (= ::pending status) (dom/props {:disabled true}))
      (Body.)
      (with-stage (AtomicEditsBehavior. dom/node status value)))))

(defn todo-edit-done [x v]
  [(assoc x :todo/checked v) ; TODO redundant in case of edits if we have `patch-dxs`
   (if false #_(zero? (rand-int 2))
     [[:db/add (:db/id x) :todo/checked v]]
     [[] ; bad tx, for demo
      [:db/add (:db/id x) :todo/checked v]])])

(defn to-dx [x]
  (map (fn [[k v]] [:db/add (:db/id x) k v]) (dissoc x :db/id)))

(defn todo-edit-text [x v]
  (let [x' (assoc x :todo/text v)]
    [x' (to-dx x')]))

(e/defn App []
  (e/client
    (dom/div (dom/props {:class "todomvc"})
      (binding [stable-kf (StableKf.)]
        (e/server
          (MasterList.
            {::authoritative-xs (query-todos db)
             ::CreateForm
             (e/fn []
               (e/client ; TODO v3 dynamic siting
                 (Field. {::stable-kf      stable-kf
                          ::value          nil
                          ::edit-fn        (fn [v]
                                             (genesis
                                               (fn [tempid] {:db/id tempid
                                                             :todo/text v,
                                                             :todo/created-at (inst-ms (js/Date.))}) ; TODO not used today, instead dxs are interpreted (see `patch-dxs`)
                                               (fn [tempid]
                                                 [[]
                                                  [:db/add tempid, :todo/text v]
                                                  [:db/add tempid, :todo/created-at (inst-ms (js/Date.))]])))
                          ::tx-parallelism ##Inf}
                   CreateNewInput)))
             ::EditForm
             (e/fn [x]
               (e/client ; TODO v3 dynamic siting
                 (dom/li #_(dom/span (dom/text (pr-str x)))
                   (concat
                     (Field. {::stable-kf stable-kf
                              ::value     (:todo/checked x false)
                              ::edit-fn   (partial todo-edit-done x)} ; FIXME v2 compiler bug when inlined: Cannot set properties of undefined (setting '3')
                       (e/fn [value status]
                         (Checkbox. {::status status ::value value} (e/fn* [] #_(dom/props ...)))))
                     (Field. {::stable-kf stable-kf
                              ::eid       (when (tempid? (stable-kf x)) (stable-kf x))
                              ::value     (:todo/text x)
                              ::edit-fn   (partial todo-edit-text x)}
                       (e/fn [value status]
                         (Input. {::status status ::value value} (e/fn* [] (dom/props {:type :text})))))))))}))))))

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

(e/defn Transactor [!tx-report conn xdxs]
  (e/server
    (e/for-by first [[txid x dx] (ignore-pendings (filter some? xdxs))]
      (let [[status _txid tx-report] (Transact!. conn dx)]
        (swap! !tx-report merge-tx-reports
          (case (ignore-pendings status)
            ::success (assoc tx-report ::last-x x, ::status ::accepted)
            ::failure {::last-x x, ::status ::rejected ::error (ex-message tx-report)}
            nil))
        nil))
    nil))

(e/defn Todo5 []
  (e/server
    (binding [conn (d/create-conn)]
      (binding [!tx-report (atom (d/transact! conn [{:todo/text "Hello", :todo/created-at (inst-ms (java.util.Date.))}
                                                    {:todo/text "world", :todo/created-at (inst-ms (java.util.Date.))}]))]
        (def repl-conn conn)
        (def repl-transact! #(reset! !tx-report (d/transact! conn %)))
        (binding [tx-report (new (m/stream (m/watch !tx-report)))] ; ensures all dependants sees individual tx-reports
          (binding [db (:db-after tx-report)]
            (e/client (dom/h1 (dom/text "todos")))
            (let [xdxs (App.)] ; xdxs :: collection of pairs [txid x dx]
              (Transactor. !tx-report conn xdxs)
              (e/client
                (dom/img (dom/props {:class "legend" :src "/hello_fiddle/state_machine.svg"}))
                (dom/pre (dom/text (contrib.str/pprint-str xdxs)))
                (hello-fiddle.todo-style/Style.)
                nil))))))))
