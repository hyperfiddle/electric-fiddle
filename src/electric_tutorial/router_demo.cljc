(ns electric-tutorial.router-demo
  (:require [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.input-zoo0 :refer [Input]]
            [hyperfiddle.router3 :as r]))

; Design Q: Are the links canonicalized or not? i.e., all imply trailing / ?

(e/defn Debug-link [p]
  (dom/code
    (r/link p (dom/text p))
    (dom/text " := " (pr-str (r/encode (r/Route-for p))))))

(e/defn Index2 [] ; /Index aka / - default document in folder
  (dom/ul
    (dom/li (Debug-link ['/ '(HelloWorld)]))
    (dom/li (Debug-link ['/ '(Tutorial)]))
    (dom/li (Debug-link ['/ '(DatomicBrowser)]))))

(e/defn HelloWorld [] (Debug-link ['/ '()]) (dom/h1 (dom/text "HelloWorld"))) ; /HelloWorld

(e/defn Tutorial1 [] ; /Tutorial/Tutorial1
  (dom/h1 (dom/text "Tutorial1"))
  (dom/div (Debug-link ['.. '(Tutorial1)]))
  (dom/div (Debug-link ['.. '(Tutorial2)])))

(e/defn Tutorial2 [] ; /Tutorial/Tutorial2
  (dom/h1 (dom/text "Tutorial2"))
  (dom/div (Debug-link ['.. '(Tutorial1)]))
  (dom/div (Debug-link ['.. '(Tutorial2)])))

(e/defn Tutorial [] ; /Tutorial - no /
  (Debug-link ['/ '()])
  (let [[page] r/route]
    (case page
      Tutorial1 (r/pop (Tutorial1))
      Tutorial2 (r/pop (Tutorial2))
      (dom/ul
        (dom/li (Debug-link ['. '(Tutorial1)]))
        (dom/li (Debug-link ['. '(Tutorial2)]))))))

(e/defn DatomicAttributes [] ; /datomic/attrs/$v
  (dom/h1 (dom/text "DatomicAttributes"))

  (r/focus [0 :left]
    (let [v r/route
          v' (Input v)]
      (r/ReplaceState! ['. v'])))

  (r/focus [0 :right]
    (let [v r/route
          v' (Input v)]
      (r/ReplaceState! ['. v'])))

  (r/focus [1]
    (let [v r/route
          v' (Input v)]
      (r/ReplaceState! ['. v'])))

  (let [v (Input (pr-str r/route))]
    (r/ReplaceState! ['. (clojure.edn/read-string v)]))

  (dom/pre (dom/text (pr-str (r/Route-at ['..])))) ; inspect parent/sibling

  (r/focus ['.. ::r/rest 0 :left] ; r/rest is what r/pop does, todo better names
    (let [v r/route
          v' (Input v)]
      (r/ReplaceState! ['. v'])))

  (dom/ul
    (dom/li (Debug-link ['.. '(DatomicEntityDetail 1)]))
    (dom/li (Debug-link ['.. '(DatomicEntityDetail 2)]))))

(e/defn DatomicTxns [] ; /datomic/txns
  (dom/h1 (dom/text "DatomicTxns")))

(e/defn DatomicEntityDetail [] ; /datomic/entity/123
  (let [[id] r/route]
    (dom/h1 (dom/text "DatomicEntityDetail " id))))

(e/defn DatomicBrowser []
  (Debug-link ['/ '()])
  (let [[page] r/route]
    (case page
      DatomicAttributes (r/pop (DatomicAttributes))
      DatomicTxns (r/pop (DatomicTxns))
      DatomicEntityDetail (r/pop (DatomicEntityDetail))
      (dom/ul
        (dom/li (Debug-link ['. '(DatomicAttributes)]))
        (dom/li (Debug-link ['. '(DatomicTxns)]))))))

(e/defn Main [ring-request]
  (e/client
    (binding [dom/node js/document.body]
      (r/router (r/HTML5-History)
        (dom/div
          (let [[page] r/route]
            (case page
              HelloWorld (r/pop (HelloWorld))
              Tutorial (r/pop (Tutorial))
              DatomicBrowser (r/pop (DatomicBrowser))
              (Index2))))))))