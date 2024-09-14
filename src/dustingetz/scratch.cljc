(ns dustingetz.scratch
  (:require [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.electric-local-def3 :as l]
            [hyperfiddle.rcf :refer [tests tap % with]]))

#_(hyperfiddle.rcf/enable!)

(tests
  (with ((l/local {}
           (tap
             (let [x (e/amb 0 1 2) y (e/amb 2 3)]
               (e/for [[= x y] [= x y]]
                 (if (= x y) [x y] (e/amb))))))
         tap tap)
    % := [2 2]))

(e/defn Scratch [])
