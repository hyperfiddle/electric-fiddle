(ns hello-fiddle.stage
  (:require [hyperfiddle.electric :as e]))

(e/def Commit)                          ; e/fn
(e/def OnCommit)                          ; e/fn
(e/def discard!)                        ; fn
(e/def stage)                           ; current stage value
(e/def stage! (fn [_]))                 ; set current stage

(e/defn* Stage [OnCommit Body]
  (let [!stage (atom nil)]
    (binding [hello-fiddle.stage/OnCommit OnCommit
              stage    (e/watch !stage)
              discard! (fn [] (reset! !stage nil))
              stage!   (partial reset! !stage)]
      (binding [Commit (e/fn rec ([] (let [stage @!stage]
                                       (case (rec. stage)
                                         (discard!))))
                         ([v] (OnCommit. v)))]
        (Body.))))
  nil)

(defmacro staged [OnCommit & body]
  `(new Stage ~OnCommit (e/fn* [] ~@body)))
