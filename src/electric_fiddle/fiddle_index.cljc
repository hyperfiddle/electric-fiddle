(ns electric-fiddle.fiddle-index
  (:require [hyperfiddle.electric3 :as e]
             [clamp-left treelister includes-str?]]
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.electric-forms0 :refer [Input* Input! Form! Checkbox]]
            [hyperfiddle.electric-scroll0 :refer [Scroll-window IndexRing]]
            [hyperfiddle.router3 :as r]))

(e/declare pages) ; inject so FiddleIndex is routable as a fiddle, also used by tutorial

(e/defn NotFoundPage [& args]
  (e/client
    (dom/h1 (dom/text "Page not found"))
    (dom/p (dom/text "Probably we broke URLs, sorry! ")
      (r/link ['/ []] (dom/text "index")))))

(e/defn TableScroll [record-count ?xs! Row]
  #_(e/server)
  (dom/div (dom/props {:class "Viewport"})
    (let [row-height 24
          [offset limit] (Scroll-window row-height record-count dom/node {:overquery-factor 1})]
      (dom/table (dom/props {:style {:position "relative" :top (str (* offset row-height) "px")}})
        (e/for [i (IndexRing limit offset)]
          (dom/tr (dom/props {:style {:--order (inc i)} :data-row-stripe (mod i 2)})
            (Row (nth (vec ?xs!) i nil)))))
      (dom/div (dom/props {:style {:height (str (clamp-left ; row count can exceed record count
                                                  (* row-height (- record-count limit)) 0) "px")}})))))

(e/defn SearchGrid [title Query Row]
  #_(r/focus [0]) ; search
  (let [!search (atom "") search (e/watch !search)
        xs! (Query search)
        n (count xs!)]
    (dom/fieldset (dom/legend (dom/text title " ")
                    (do (reset! !search (e/client (Input* ""))) nil)
                    (dom/text " (" n " items)"))
      (TableScroll n xs! Row))))

(declare css)
(e/defn FiddleIndex []
  (e/client (dom/style (dom/text css)) (dom/props {:class "Explorer"})
    ;; (dom/pre (dom/text (pr-str r/route)))
    #_(dom/pre (dom/text (e/server (pprint-str (fiddles-by-ns-segments pages)))))
    (SearchGrid (str `FiddleIndex)
      (e/fn [search] (vec (->> (sort-by key pages)
                            (filter (fn [[qs F]] (includes-str? (str qs) search))))))
      (e/fn Row [[qs F :as ?x]]
        (when ?x
          (dom/tr
            (dom/td (r/link [qs] (dom/text (name qs))))
            (dom/td (dom/text qs))))))))

(e/defn FiddleRoot
  [fiddles
   & {:keys [default]
      :or {default `(FiddleIndex)}}]
  #_(dom/pre (dom/text (pr-str r/route)))
  (let [[fiddle & _] r/route]
    (if-not fiddle (r/ReplaceState! ['. default])
      (let [Fiddle (get fiddles fiddle NotFoundPage)]
        (set! (.-title js/document) (str (some-> fiddle name (str " – ")) "Electric Fiddle"))
        (binding [pages fiddles] ; todo untangle - tutorial uses some fiddle infrastructure, perhaps should use more?
          (case fiddle
            `FiddleIndex #_(FiddleIndex) (Fiddle) ; lol why - workaround crash 20241205
            (r/pop (Fiddle))))))))

(e/defn FiddleMain [ring-req fiddles & {:as props}] ; dev, optionally in prod (e.g. tutorial)
  (binding [e/http-request (e/server ring-req)
            dom/node js/document.body]
    (dom/div ; mandatory wrapper div https://github.com/hyperfiddle/electric/issues/74
      (r/router (r/HTML5-History)
        (FiddleRoot (merge {`FiddleIndex FiddleIndex} fiddles) props)))))

(def css "
/* Scroll machinery */
.Explorer { position: fixed; } /* mobile: don't allow momentum scrolling on page */
.Explorer .Viewport { overflow-x:hidden; overflow-y:auto; }
.Explorer table { display: grid; }
.Explorer table tr { display: contents; visibility: var(--visibility); }
.Explorer table td { grid-row: var(--order); }
.Explorer div.Viewport { height: 100%; }

/* Userland layout */
.Explorer fieldset { position:fixed; top:0; bottom:0; left:0; right:0; }
.Explorer table { grid-template-columns: 20em auto; }

/* Cosmetic */
.Explorer fieldset { padding: 0; padding-left: 0.5em; background-color: white; }
.Explorer legend { margin-left: 1em; font-size: larger; }
.Explorer legend > input[type=text] { vertical-align: middle; }
.Explorer table td { height: 24px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.Explorer table tr[data-row-stripe='0'] td { background-color: #f2f2f2; }
.Explorer table tr:hover td { background-color: #ddd; }
")