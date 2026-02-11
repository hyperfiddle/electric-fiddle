(ns electric-essay.tutorial-app
  (:require clojure.edn
            #?(:clj [clojure.java.io :as io])
            clojure.string
            [contrib.data :refer [index-by]]
            [contrib.clojurex :refer [#?(:clj slurp-safe)]]
            [electric-essay.fiddle-markdown :refer [Custom-markdown Fiddle-markdown-extensions]]
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.electric-svg3 :as svg]
            [hyperfiddle.router5 :as r]))

(defn index-essay-index [essay-index]
  (->> essay-index
    (mapcat (fn [[group essays]] essays))
    (map-indexed (fn [idx entry] {::order idx ::id entry}))
    (index-by ::id)))

(e/defn Get-prev-next [essay-index page]
  (let [tutorials-seq (vec (sort-by ::order (vals essay-index)))]
    (when-let [order (::order (essay-index page))]
      [(get tutorials-seq (dec order))
       (get tutorials-seq (inc order))])))

(defn title [m] (name (::id m)))

(e/defn Nav [sitemap cur-page footer?] #_[& [directive alt-text target-s ?wrap :as route]]
  (e/client
    (let [essay-index (index-essay-index sitemap)
          [prev next] (Get-prev-next essay-index cur-page)]
      #_(println `prev cur-page prev next)
      (dom/div {} (dom/props {:class [(if footer? "user-examples-footer-nav" "user-examples-nav")
                                      (when-not prev "user-examples-nav-start")
                                      (when-not next "user-examples-nav-end")]})
        (when prev
          (r/link ['. [(::id prev)]] ; why nested?
            (dom/props {:class "user-examples-nav-prev"})
            (dom/text (str "< " (title prev)))))
        (dom/div (dom/props {:class "user-examples-select"})
          (svg/svg (dom/props {:viewBox "0 0 20 20"})
            (svg/path (dom/props {:d "M19 4a1 1 0 01-1 1H2a1 1 0 010-2h16a1 1 0 011 1zm0 6a1 1 0 01-1 1H2a1 1 0 110-2h16a1 1 0 011 1zm-1 7a1 1 0 100-2H2a1 1 0 100 2h16z"})))
          (dom/select
            (e/for [[group-label entries] (e/diff-by {} sitemap)]
              (dom/optgroup (dom/props {:label group-label})
                (e/for [page (e/diff-by {} entries)]
                  (let [m (essay-index page)]
                    (dom/option
                      (dom/props {:value (str page) :selected (= cur-page page)})
                      (dom/text (str (inc (::order m)) ". " (title m))))))))
            (when-some [^js e (dom/On "change" identity nil)]
              (let [[done! err] (e/Token e)]
                (when done!
                  (done! (r/Navigate! ['. [(clojure.edn/read-string (.. e -target -value))]])))))))
        (when next
          (r/link ['. [(::id next)]] ; why nested?
            (dom/props {:class "user-examples-nav-next"})
            (dom/text (str (title next) " >"))))))))

(defn namespace-name [qualified-symbol]
  (some-> qualified-symbol namespace
    (clojure.string/split #"\.") last
    (clojure.string/replace "-" "_")))
(comment (namespace-name `Forms3a-form) := "forms3a_form")

(e/defn Consulting-banner []
  (dom/p (dom/text "Managers of growth stage businesses, hire us! ")
    (dom/a (dom/text "Consulting brochure here") (dom/props {:href "https://gist.github.com/dustingetz/c40cde24a393a686e26bce73391cd20f"}))))

(e/defn Tutorial [essay-config essay-md-folder]
  (e/client
    (dom/props {:class "Tutorial"})
    (dom/style (dom/text (e/server (some-> (io/resource "electric_essay/tutorial.css") slurp-safe))))
    (let [[?essay-filename & _] r/route]
      (if-not ?essay-filename (r/ReplaceState! ['. [(first (second (first essay-config)))]]) ; "two_clocks.md" encodes to /'two_clocks.md'
        (do
          #_(Consulting-banner)
          (when-some [e (dom/Await-element dom/node "#title-extra")]
            (binding [dom/node e]
              (dom/span                                     ; root wrapper div order issue
                (dom/text "— Electric v3 ")
                (dom/a (dom/text "(github)") (dom/props {:href "https://github.com/hyperfiddle/electric"})))))
          (when-some [e (dom/Await-element dom/node "#nav")]
            (binding [dom/node e]
              (Nav essay-config ?essay-filename false)))
          (e/server
            (if-some [md-content (slurp-safe (str essay-md-folder ?essay-filename ".md"))]
              (Custom-markdown (Fiddle-markdown-extensions) md-content)
              (e/client (do (dom/h1 (dom/text "Tutorial page not found: " ?essay-filename))
                            (dom/p (dom/text "Probably we broke URLs, sorry! ")
                                   (r/link ['. []] (dom/text "tutorial index")))))))
          #_(Nav essay-config ?essay-filename true))))))

(e/defn Examples ; Fork of Tutorial with no navigation and no chrome.
  [essay-config essay-md-folder]
  (e/client
    (dom/props {:class "Tutorial"})
    (dom/style (dom/text (e/server (some-> (io/resource "electric_essay/tutorial.css") slurp-safe))))
    (let [[?essay-filename & _] r/route]
      (if-not ?essay-filename
        (r/ReplaceState! ['. [(first (second (first essay-config)))]]) ; "two_clocks.md" encodes to /'two_clocks.md'
        (if-some [md-content (e/server (slurp-safe (str essay-md-folder ?essay-filename ".md")))]
          (Custom-markdown (Fiddle-markdown-extensions) md-content)
          (dom/h1 (dom/text "Example not found: " ?essay-filename)))))))
