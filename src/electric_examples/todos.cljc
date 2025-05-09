(ns electric-tutorial.todos
  (:require #_[clojure.core.match :refer [match]]
            #?(:clj [datascript.core :as d])
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.electric-forms3 :as cqrs
             :refer [Input! Checkbox! Checkbox* Button! Form!
                     Service PendingController try-ok effects*]]
            [dustingetz.trivial-datascript-form :refer [#?(:clj transact-unreliable)]]))

(defn stable-kf [tempids-rev {:keys [:db/id]}]
  (let [id (if (fn? id) (str (hash id)) id)]
    (prn "stable-kf" id '-> (tempids-rev id))
    (tempids-rev id id)))

(defn fast-stable-kf [get-tempids-rev {:keys [:db/id]}]
  (let [tempids-rev (get-tempids-rev)
        id (if (fn? id) (str (hash id)) id)]
    (prn "fast-stable-kf" id '-> (tempids-rev id))
    (tempids-rev id id)))

(e/defn Todo-records [db tempids forms] ; todo field awareness
  (e/client
    (let [tempids-rev (clojure.set/map-invert tempids)
          kf (partial fast-stable-kf ; passed to e/diff-by, crazy slowdown if fn value updates
               ((e/capture-fn) (constantly (doto tempids-rev (prn "generate new stable-kf")))))]
      (PendingController kf :task/description forms ; rebind transact to with, db must escape
        (e/server
          (e/diff-by :db/id
            (e/Offload ; good reason to not require offload to have # ?
              #(try
                 (prn "query records")
                 (->> (d/q '[:find [(pull ?e [:db/id
                                              :task/status
                                              :task/description]) ...]
                             :where [?e :task/status]] db)
                   (sort-by :task/description))
                 (catch Exception e (prn e))))))))))

; if there is a button type submit in the form, and you press enter in an input, the form will submit
; checkboxes don't do that (don't have enter behavior - toggle with space, enter is noop), therefore you need
; auto-submit true on the form if you want inputs to auto-submit.

(e/declare debug*)
(e/declare slow*)
(e/declare fail*)
(e/declare show-buttons*)

(e/defn TodoCreate []
  (Form! (Input! ::create "" :placeholder "Buy milk") ; press enter
    :genesis true ; immediately consume form, ready for next submit
    :commit (fn [{v ::create :as dirty-form} tempid]
              (prn 'TodoCreate-commit dirty-form)
              [[`Create-todo tempid v] {tempid {:db/id tempid :task/description v :task/status :active}}])
    :show-buttons show-buttons* :debug debug*))

(e/defn TodoItem [{:keys [db/id task/status task/description ::cqrs/pending] :as m}]
  (dom/li
    (Form!
      (e/amb
        (Form! (Checkbox! :task/status (= :done status)) ; FIXME clicking commit button on top of autocommit generates broken doubled tx
          :name ::toggle
          :commit (fn [{v :task/status}] [[`Toggle id (if v :done :active)]
                                          {id (-> m (dissoc ::pending) (assoc :task/status v))}])
          :show-buttons show-buttons* :auto-submit (not show-buttons*))
        (Form! (Input! :task/description description)
          :name ::edit-desc
          :commit (fn [{v :task/description}] [[`Edit-todo-desc id v]
                                               {id (assoc m :task/description v)}])
          :show-buttons show-buttons*
          :debug debug*)
        (Form! (Button! nil :label "X" :class "destroy" :disabled (some? pending))
          :auto-submit (not show-buttons*) :show-buttons show-buttons*
          :name ::destroy
          :commit (fn [_] [[`Delete-todo id] {id ::cqrs/retract}]))

        (if-let [[t xcmd guess] pending]
          [t {::pending xcmd} guess]
          (e/amb)))
      :auto-submit (not show-buttons*) :show-buttons show-buttons* :debug debug*
      :commit (fn [{:keys [::toggle ::edit-desc ::destroy ::pending]}]
                [[`Batch toggle edit-desc destroy pending] {}]
                #_(let [[_ id status] toggle
                        [_ id v] create
                        [_ id v'] edit-desc
                        [_ id] destroy]
                    (cond
                      (and create destroy) nil #_[nil dirty-form-guess]
                      (and create edit) [[`Create-todo (or v' v)] dirty-form-guess]))))))

(e/defn TodoList [db tempids forms]
  (dom/div (dom/props {:class "todo-list"})
    (let [forms' (TodoCreate) ; transfer responsibility to pending item form
          todos (Todo-records db tempids (e/amb forms forms'))]
      (e/amb
        (dom/ul (dom/props {:class "todo-items"})
          (e/for [m todos]
            (TodoItem m)))
        (dom/p (dom/text (e/Count todos) " items left"))))))

#?(:clj (def !conn (doto (d/create-conn {})
                     (d/transact!
                       [{:task/description "feed baby" :task/status :active}
                        {:task/description "buy milk" :task/status :active}
                        {:task/description "call mom" :task/status :active}]))))

(e/declare !tx-report)

(defn accumulate-tx-reports! [!tx-report new-tx-report]
  (swap! !tx-report (fn [{:keys [tempids] :as tx-report}]
                      (merge tx-report (update new-tx-report :tempids (fn [old-tempids] (merge old-tempids tempids)))))))

(e/defn Create-todo [tempid desc]
  (let [serializable-tempid (str (hash tempid))]
    (e/server
      (let [tx [{:task/description desc, :task/status :active :db/id serializable-tempid}]]
        (e/Offload #(try-ok (accumulate-tx-reports! !tx-report
                              (transact-unreliable !conn tx :slow slow* :fail fail*))))))))

(e/defn Edit-todo-desc [id desc]
  (e/server
    (let [tx [{:db/id id :task/description desc}]]
      (e/Offload #(try-ok (accumulate-tx-reports! !tx-report
                            (transact-unreliable !conn tx :slow slow* :fail fail*)))))))

(e/defn Toggle [id status]
  (e/server
    (let [tx [{:db/id id, :task/status status}]]
      (e/Offload #(try-ok (accumulate-tx-reports! !tx-report
                            (transact-unreliable !conn tx :slow slow* :fail fail*)))))))

(e/defn Delete-todo [id] ; FIXME retractEntity works but todo item stays on screen, who is retaining it?
  (e/server
    (let [tx [[:db/retractEntity id]]]
      (e/Offload #(try-ok (accumulate-tx-reports! !tx-report
                            (transact-unreliable !conn tx :slow slow* :fail fail*)))))))

;; WIP - working but questionable and has duplicate code with cqrs/Service
;; Can we avoid batching entirely?
;; same pattern as m/join
(e/defn Batch [& forms]
  (prn 'Batch forms)
  (let [forms (filter some? forms)
        results (e/for [form (e/diff-by {} forms)] ; diff by effect position, not great
                  (e/When form
                    (let [[effect & args] form
                          Effect (effects* effect (e/fn default [& args] (doto ::effect-not-found (prn effect))))
                          res #_[t form guess db] (e/Apply Effect args)] ; effect handlers span client and server
                      res)))
        results (e/as-vec results)]
    (if (= (count results) (count forms)) ; poor man's m/join
      (cond
        (every? nil? results) nil
        (every? #{::cqrs/ok} results) ::cqrs/ok
        () (some some? results))
      (e/amb)))) ; wait until done

(e/defn Todos []
  (e/client
    (binding [effects* {`Create-todo Create-todo
                        `Edit-todo-desc Edit-todo-desc
                        `Toggle Toggle
                        `Delete-todo Delete-todo
                        `Batch Batch}
              debug* (Checkbox* false :label "debug")
              slow* (Checkbox* true :label "latency")
              fail* (Checkbox* false :label "failure" :disabled true)
              show-buttons* (Checkbox* false :label "show-buttons")
              !tx-report (e/server (atom {:db-after @!conn}))]
      debug* slow* fail* show-buttons*

      ;; on create-new submit, in order:
      ;; 1. transact! is called
      ;; 2. submit token is spent
      ;;   3. create-new's OnAll branch unmounts, and so the parent Form!, and so the PendingController optimistic branch
      ;;   4. PendingController *should* retract the pending edit on unmount, but this crashes the app today.
      ;; 5. either looped tempids or (e/watch !conn) propagates first
      ;; 6. PendingController sees new query result and new tempids
      ;;   - PendingController reconciles new entity with existing (!) branch
      ;;     - PendingController reverts tempdis and has a stable-kf
      ;; Problems:
      ;; - PendingController retracts the edit on token burn, before the authoritative entity is queried
      ;;   - the todo row is unmounted before the query reruns.
      ;; - as of today, PendingController crashes the app (infinite loop) on edit retraction
      ;;   - if we don't retract the pending edit, TodoList loops and we get end up with two created entries instead of one.
      (let [tx-report (e/server (e/watch !tx-report))
            db (e/server (:db-after tx-report))
            tempids (e/server (:tempids tx-report))] ; TODO new db propagates before new tempids, so we see a transitory row, we must ensure db and tempids update at the same time.
        (Service
          (e/with-cycle* first [forms (e/amb)]
            (e/Filter some?
              (TodoList db tempids forms))))))))
