(ns electric-tutorial.forms6-inline-submit-builtin
  (:require #?(:clj [datascript.core :as d])
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.cqrs0 :as forms :refer [Field]]
            [hyperfiddle.input-zoo0 :refer [CheckboxSubmit! Button!]]
            [electric-tutorial.forms3-crud :refer [Service #?(:clj !conn)]]))

;; TODO adapted from input-zoo0/InputSubmit!
;; TODO refactor / unify with InputSubmit!
;; TODO move to input zoo
(e/defn UserFriendlyInputSubmit! [v & {:keys [maxlength type token] :as props
                                       :or {maxlength 100 type "text"}}]
  ; like Input! but with commit/discard affordance at value level
  ; dirty when you dirty, you can blur and it stays ditry
  ; submit with enter, tab, or commit button
  ; discard with esc or discard
  ; todo also listen for meta keys

  ; event strategy: use "input" because it works on numeric, and also keydown reads
  ; the value before the node target value is set.
  ; separately, grab esc/cancel somehow (and it must be checkbox compatible)
  (e/client
    (dom/input
      (dom/props (-> props (dissoc :token) (assoc :maxLength maxlength :type type)))
      (let [e (dom/On "input" identity nil) [t err] (e/RetryToken e)
            editing? (dom/Focused?)
            waiting-mine? (some? t)
            waiting? (or (some? t) (some? token))
            error? (some? err) ; todo route error from original token
            dirty? (or editing? waiting-mine? error?)]
        (prn 'InputSubmit! editing? waiting-mine? waiting? error? dirty?) ; prove glitch
        (when-not dirty? (set! (.-value dom/node) v))
        (when waiting? (dom/props {:aria-busy true}))
        (when error?   (dom/props {:aria-invalid true})) ; glitch
        (let [[t v] (if waiting?
                      [(or t token)   ; absorb foreign token
                       ((fn [] (cond
                                 t (some-> e .-target .-value (subs 0 maxlength)) ; local override
                                 token v)))]
                      (e/amb)) ; tricky amb

              ;; [t v e] - t can be burned but e & v remains = retry ?

              external-submit? (some? token) ; inlining this under when causes a glitch NPE in commit
              ;; q (and external-submit? (not= t token))
              dirty? (e/Some? t)
              locally-dirty? (and (e/Some? t) (not= t token)) ; cancel inflight txn in this case - todo
              can-commit? (or locally-dirty? (some? err))

              [us _ :as btns]
              (e/amb
                #_(binding [dom/node parent-node]
                    (e/amb
                      (Button! ::commit :label "commit" :disabled (not can-commit?)) ; todo progress
                      (Button! ::discard :label "discard" :disabled (not dirty?))))
                (let [e (e/Filter some?
                          (e/amb (dom/On "keypress" #(when (and can-commit? (= "Enter" (.-key %))) %) nil) ; could be merged into one event handler
                                 (dom/On "keyup" #(when (and can-commit? (= "Escape" (.-key %))) %) nil))) ; supports cancellation because Escape will overwrite Enter's `e`
                      [t' err] (e/RetryToken e)
                      command (case (.-key e)
                                "Enter"  ::commit
                                "Escape" ::discard
                                nil)]
                  (prn 'Action! command t' err)
                  (when err (dom/props {:aria-invalid true})) ; on input itself
                  (if t'
                    (do (when (= ::discard command) (e/on-unmount #(.blur dom/node))) ; blur input to reset value *after* token is consumed. Otherwise would trigger blur event while ::discard is in flight.
                        [t' command])
                    (e/amb))))

              _ external-submit? ; workaround crash on discard in todos

              ;; _ (prn 'edit (e/Some? t) (e/as-vec v)) (prn 'btns (e/as-vec btns))
              ]
          (e/for [[u cmd] btns]
            (case cmd
              ::discard (case ((fn [] (us) (t) (when external-submit? (token)))) ; clear any in-flight commit yet outstanding
                          (e/amb)) ; clear edits, controlled form will reset
              ::commit [(fn token ; double burn is harmless when (= t token)
                          ([] (u) (t) (when external-submit? (token))) ; success, burn both commit token and field token
                          ([err] (u err))) ; keep uncommited field, present retry
                        v])))))))

(e/defn UserFriendlyCheckboxSubmit!
  [checked & {:keys [id label #_token] :as props
              :or {id (random-uuid)}}]
  ; checkbox - cannot discard, submit on toggle interaction.
  ; failure will discard and highlight red
  ; todo attach to in-flight submit
  (e/client
    (e/amb
      (let [[e t err input-node]
            (dom/input
              (dom/props {:type "checkbox", :id id}) (dom/props (dissoc props :id :label))
              (let [e (dom/On "change" identity) [t err] (e/RetryToken e)] ; single txn, no concurrency
                [e t err dom/node]))
            editing? (dom/Focused? input-node)
            waiting? (some? t)
            error? (some? err)
            dirty? (or editing? waiting? error?)]
        (when-not dirty? (set! (.-checked input-node) checked))
        (when error? (dom/props input-node {:aria-invalid true}))
        (when waiting? (dom/props input-node {:aria-busy true}))
        (let [[t v] (if waiting? [t ((fn [] (-> e .-target .-checked)))] (e/amb))
              [us _ :as btns]
              #_(e/amb ; todo wire to input esc/enter
                (Button! ::commit :label "commit" :disabled (not (e/Some? t))) ; todo progress
                (Button! ::discard :label "discard" :disabled (not (e/Some? t))))
              (let [e (e/Filter some?
                          (e/amb (dom/On "change" #(when (and #_can-commit? true #_(= "Enter" (.-key %))) %) nil) ; could be merged into one event handler
                                 (dom/On "keyup" #(when (and #_can-commit? true (= "Escape" (.-key %))) %) nil))) ; supports cancellation because Escape will overwrite Enter's `e`
                      [t' err] (e/RetryToken e)
                      command (case (.-type e)
                                "change" ::commit
                                "keyup"  ::discard
                                nil)]
                  (prn 'Action! command t' err)
                  (when err
                    (dom/props input-node {:aria-invalid true})
                    (set! (.-checked input-node) checked))
                  (if t'
                    (do (when (= ::discard command) (e/on-unmount #(.blur input-node))) ; blur input to reset value *after* token is consumed. Otherwise would trigger blur event while ::discard is in flight.
                        [t' command])
                    (e/amb)))]
          (e/for [[u cmd] btns]
            (case cmd
              ::discard (case ((fn [] (us) (t))) ; clear any in-flight commit yet outstanding
                          (e/amb)) ; clear edits, controlled form will reset
              ::commit [(fn token
                          ([] (u) (t)) ; success, burn both commit token and field token
                          ([err] (u err))) ; keep uncommited field, present retry
                        v]))))
      (e/When label (dom/label (dom/props {:for id}) (dom/text label))))))

(e/defn UserForm [db id]
  (dom/fieldset
    (let [{:keys [user/str1 user/num1 user/bool1]}
          (e/server (d/pull db [:user/str1 :user/num1 :user/bool1] id))]
      (dom/dl
        (e/amb
          (dom/dt (dom/text "str1"))
          (dom/dd (Field id :user/str1
                    (UserFriendlyInputSubmit! str1))) ; bundled commit/discard

          (dom/dt (dom/text "num1"))
          (dom/dd (Field id :user/num1
                    (e/for [[t v] (UserFriendlyInputSubmit! num1 :type "number")] ; bundled commit/discard
                      [t (parse-long v)])))

          (dom/dt (dom/text "bool1"))
          (dom/dd (Field id :user/bool1
                    (UserFriendlyCheckboxSubmit! bool1)))))))) ; bundled commit/discard

(e/defn Forms6-inline-submit-builtin []
  (let [db (e/server (e/watch !conn))
        edits (e/amb
                (UserForm db 42) #_(Stage :debug true) ; NO stage at form level
                (UserForm db 42) #_(Stage :debug true))]
    (Service edits)))
