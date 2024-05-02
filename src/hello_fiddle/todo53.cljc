;; - WIP - Separate Popover/Stage layer from UI controls
;; Next up:
;; - Add Form abstraction
;; - Figure out Form/Field/Input's API
;; - Figure out how to cancel pending txs — if even needed

(ns hello-fiddle.todo53
  (:require
   [hyperfiddle.electric :as e]
   [hyperfiddle.electric-css :as css]
   [hyperfiddle.electric-dom2 :as dom]
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
     (reset! !x# ~x)
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

(defn resolve-tempid-rev [tempids id]
  (get (zipmap (vals tempids) (keys tempids)) id))

(defn about-entity? [stable-kf x [op e a v]]
  (and (#{:db/add :db/ret} op)
    (= (stable-kf x) e)))

(comment
  (about-entity? :db/id {:db/id 1} [:db/add 1 :foo :bar]) := true
  (about-entity? :db/id {:db/id 1} [:db/add 2 :foo :bar]) := false
  (about-entity? :db/id {:db/id 1} [:db/retractEntity 2]) := false
  )

(defn patch-dx [stable-kf x [op _e a v :as dx]]
  (if (about-entity? stable-kf x dx)
    (case op
      :db/add (assoc x a v)
      :db/ret (dissoc x a))
    x))

(comment
  (patch-dx :db/id {:db/id 1} [:db/add 1 :test/text "Hello"]) := {:db/id 1, :test/text "Hello"}
  (patch-dx :db/id {:db/id 1} [:db/add 2 :test/text "Hello"]) := {:db/id 1}
  (patch-dx :db/id {:db/id 1 :test/text "Hello"} [:db/ret 1 :test/text]) := {:db/id 1}
  (patch-dx :db/id {:db/id 1 :test/text "Hello"} [:db/ret 2 :test/text]) := {:db/id 1, :test/text "Hello"}
  )

(defn patch-dxs [stable-kf x dxs]
  (reduce (partial patch-dx stable-kf) x dxs))

(comment
  (patch-dxs :db/id {:db/id 1} [[:db/add 1 :test/text "Hello"]
                                [:db/add 1 :test/value 42]]) 
  := {:db/id 1, :test/text "Hello", :test/value 42}
  )

(defn stable-kf [entity] (:db/id entity)) ; FIXME stabilize tempid->id resolution

(defn genesis [x! dx!] ;; TODO looks like x! is unused. Why? Is dx! enough to model genesis? x₀ = id ⊕ dx₀ = dx₀ ?
  (let [tempid (- (Math/abs (hash (random-uuid))))]
    [(x! tempid) (dx! tempid)]))

(e/defn TxEmitter [tx-parallelism]
  (let [!txs (atom ())]
    [(e/watch !txs)
     (fn emit! [tx] (swap! !txs #(cons tx (take (dec tx-parallelism) %))) nil)
     (fn retract! [tx] (swap! !txs (partial remove #{tx})) nil)]))

(e/defn Drop [n value]
  (new (m/relieve {} (m/reductions {} nil (m/eduction (drop n) (e/fn* [] value))))))

(e/defn TxMonitor [tx] ; TODO could two = txs race?
  (e/server
    (let [{::keys [accepted rejected error]} (Drop. 1 tx-report)] ; ignore current tx-report, only look at next one
      (cond (= accepted tx) [::accepted]
            (= rejected tx) [::rejected (ex-message error)]
            :else           nil))))

(defn optimistic [stable-kf xdxs authoritative-xs]
  (let [index (contrib.data/index-by stable-kf authoritative-xs)]
    (vals (reduce (fn [index [x dxs]]
                    (if (contains? index (stable-kf x))
                      (update index (stable-kf x) #(patch-dxs stable-kf % dxs))
                      (assoc index (stable-kf x) x)))
            index xdxs))))

(e/defn Field [{::keys [value edit-fn tx-parallelism]
                :or    {tx-parallelism 1}} Body]
  (let [!status               (atom ::idle)
        [xdxs emit! retract!] (TxEmitter. tx-parallelism)
        !error                (atom nil), error (e/watch !error)]
    ((fn [xdxs] (when (not-empty xdxs) (reset! !status ::pending))) xdxs)
    (e/for-by identity [xdx xdxs]
      (let [[status error] (TxMonitor. (second xdx))]
        (case status
          ::accepted (do (retract! xdx) (reset! !status ::accepted)  (reset! !error nil))
          ::rejected (do (retract! xdx) (reset! !status ::rejected)  (reset! !error error))
          nil)))
    (when-some [v (Body. value (e/watch !status))]
      ((fn [v] (emit! (edit-fn v))) v))
    xdxs))

(e/defn MasterList [{::keys [authoritative-xs CreateForm EditForm]}]
  (e/client
    (let [!xdxs         (atom ())
          optimistic-xs (optimistic #(:db/id %1 %2) (e/watch !xdxs) authoritative-xs); FIXME not compatible with virtual scroll, authoritative-xs transfers entirely
          ]
      (reset! !xdxs
        (concat
          (e/server  ; TODO v3 dynamic siting
            (CreateForm.))
          (dom/ul
            (apply concat
              (e/for-by :db/id [x (sort-by :db/id optimistic-xs)] ; FIXME stabilize ; FIXME for-by on wrong peer
                (e/server  ; TODO v3 dynamic siting
                  (EditForm. x))))))))))

(e/defn CommitOnBlurBehavior "Commit current stage when given `node` is blured.
An input can be blurred e.g. by clicking outside or pressing Tab."
  [node]
  (when-let [[_event done! running?] (new (listen node "blur"))]
    (when running?
      (case (stage/Commit.) ; assumed called in a stage
        (done!)))))

(e/defn AtomicEditsBehavior
  "Augment a dom input so it accumulate edits and:
  - emits the latest one on Enter pressed (submit)
  - cancel the edits and resets the input to the latest authoritative value (discard)
   The input content will reflect the authoritative value unless:
   - user has focused the input,
   - user has staged edits."
  [node status value]
  ;; stage typed text
  (when-let [[value' done! _running?] (new (listen node "input" #(.. % -target -value)))]
    (stage/stage! value')
    (done!))
  ;; Emit (commit) on Enter pressed / discard on Escape
  (when-let [[event done! _] (new (listen node "keyup"))]
    (done!)
    (case (.-key event)
      "Enter"  (stage/Commit.)
      "Escape" (do (stage/discard!) (.blur node))
      nil))
  (let [value  (or stage/stage value) ; don't damage user uncommitted typing
        status (cond (some? stage/stage) ::dirty
                     :else               status)]
    (TxUI. status) ; Render 4 colors ; FIXME misplaced, too low level here
    (when-not (dom/Focused?.) ; don't alter input while user-focused (UX)
      (set! (.-value node) value))))

(e/defn ClearOnSubmitBehavior
  "Clear an input on submit. A common pattern to chat interfaces and todo-apps"
  [node]
  (when (empty? stage/stage)
    (when (not-empty (.-value node)) ; not composed in a single (and …) so expr reruns
      (set! (.-value node) nil))))

(e/defn SpreadSheetCellBehavior
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
    (e/watch !value)))

(defmacro with-stage
  "Run `Body` in a `stage` and return latest committed value."
  [& body]
  `(new WithStage (e/fn* [] ~@body)))

(e/defn Input [{::keys [status value]} Body]
  (e/client
    (dom/input
      (Body.)
      (with-stage (SpreadSheetCellBehavior. dom/node status value)))))

(e/defn CreateNewInput [v0 status tx!] ; Not a regular input, doesn't hold on value, do not care about tx success/failure
  (e/client
    (dom/input
      (with-stage
        (AtomicEditsBehavior. dom/node status v0)
        (ClearOnSubmitBehavior. dom/node)))))

;; TODO remove
(e/defn InputController [node event-type v0 status read write! Body]
  (let [[value done! running? :as _triple] (new (listen node event-type (let [cb ((capture) read)] #((cb) %))))
        value   (if (some? value) value v0)
        done!   ((capture) done!)
        !status (atom (e/snapshot status))]
    (reset! !status status)
    (let [status' (e/watch !status)] ; TODO rename status' -> status
      (TxUI. status')
      (Body. status' value write! #((done!))))))

;; TODO move to edit behaviors abstraction
(e/defn Checkbox [{::keys [status value]} Body]
  (e/client
    (dom/input
      (dom/props {:type :checkbox})
      (when (= ::pending status) (dom/props {:disabled true}))
      (Body.)
      (InputController. dom/node "change" value status #(.. % -target -checked) #(set! (.-checked dom/node) %)
        (e/fn* [status value' write! done!]
          (prn "checkbox" status value')
          (cond
            #_#_ (doto (dom/Focused?.) (prn "focused")) value ; ignore concurrent modifications while typing ; not appropriate for checkboxes

            (= ::accepted status)         (do (done!) (write! value) value)
            (= ::rejected status)         (do (done!) (write! value) nil)
            (#{::dirty ::pending} status) value' ; ignore concurrent modifications until accepted ; TODO add popover ::dirty
            ))))))

(defn todo-edit-done [x v]
  [(assoc x :todo/checked v) ; TODO redundant in case of edits if we have `patch-dxs`
   (if (zero? (rand-int 2))
     [[:db/add (:db/id x) :todo/checked v]]
     [[] ; bad tx, for demo
      [:db/add (:db/id x) :todo/checked v]])])

(defn todo-edit-text [x v]
  [{:db/id (:db/id x) :todo/text v}
   [[:db/add (:db/id x) :todo/text v]]])

(e/defn App []
  (e/server
    (MasterList.
      {::authoritative-xs (query-todos db)
       ::CreateForm (e/fn []
                      (e/client ; TODO v3 dynamic siting
                        (Field. {::value   nil
                                 ::edit-fn (fn [v] (genesis
                                                     (fn [tempid] {:db/id tempid, :todo/text v}) ; TODO not used today, instead dxs are interpreted (see `patch-dxs`)
                                                     (fn [tempid] [[:db/add tempid :todo/text v]])))
                                 ::tx-parallelism ##Inf}
                          CreateNewInput)))
       ::EditForm (e/fn [x]
                    (e/client ; TODO v3 dynamic siting
                      (dom/li (dom/span (dom/text (pr-str x)))
                              (concat
                                (Field. {::value   (:todo/checked x false)
                                         ::edit-fn (partial todo-edit-done x)} ; FIXME v2 compiler bug when inlined: Cannot set properties of undefined (setting '3')
                                  (e/fn [value status]
                                    (Checkbox. {::status status ::value  value} (e/fn* [] #_(dom/props ...)))))
                                (Field. {::value   (:todo/text x)
                                         ::edit-fn (partial todo-edit-text x)}
                                  (e/fn [value status]
                                    (Input. {::status status ::value value} (e/fn* [] (dom/props {:type :text})))))))))})))

(e/defn Transactor [!tx-report conn dxs]
  (e/server
    (let [dxs (let [!no-pending (atom (e/snapshot dxs))]
                (try (reset! !no-pending dxs) (catch hyperfiddle.electric.Pending _))
                (e/watch !no-pending))]
      (e/for-by identity [dx dxs]
        (let [[status _txid tx-report] (Transact!. conn dx)]
          (case (ignore-pendings status)
            ::success (reset! !tx-report (assoc tx-report ::accepted dx))
            ::failure (swap! !tx-report assoc ::rejected dx, ::error tx-report)
            nil)
          nil)))))

(e/defn Todo5 []
  (e/server
    (binding [conn (d/create-conn)]
      (binding [!tx-report (atom (d/transact! conn [{:db/id "-1", :todo/text "Hello"}
                                                    {:db/id "-2", :todo/text "world"}]))]

        (def repl-conn conn)
        (def repl-transact! #(reset! !tx-report (d/transact! conn %)))
        (binding [tx-report (new (m/stream (m/watch !tx-report)))] ; ensures all dependants sees individual tx-reports
          (binding [db (:db-after tx-report)]
            (let [xdxs (App.)
                  dxs (remove nil? (map second xdxs))]
              (Transactor. !tx-report conn dxs)
              (e/client
                (dom/pre (dom/text (contrib.str/pprint-str xdxs)))
                (css/style
                  (css/keyframes "spin"
                    (css/keyframe :from {:transform "rotate(0deg)"})
                    (css/keyframe :to   {:transform "rotate(360deg)"}))
                  (css/rule "ul li" {:display :grid, :grid-auto-flow :column, :width :fit-content}
                    (css/rule "input[type='checkbox']" {:grid-column 1})
                    (css/rule "input[type='text']" {:grid-column 2})
                    )
                  (css/rule "input"
                    (css/rule {:outline "2px solid gray"})
                    (css/rule "&.dirty"   {:outline-color "orange"})
                    (css/rule "&.pending" {:outline-color "yellow"}
                      (css/rule "&[type=\"checkbox\"]" {:animation "spin 1s linear infinite"}))
                    (css/rule "&.success" {:outline-color "green"})
                    (css/rule "&.failure" {:outline-color "red"})) )
                nil))))))))
