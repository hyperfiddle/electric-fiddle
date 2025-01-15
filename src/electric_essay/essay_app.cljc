(ns electric-essay.essay-app
  (:require clojure.edn
            #?(:clj [clojure.java.io :as io])
            clojure.string
            [contrib.clojurex :refer [#?(:clj slurp-safe)]]
            [electric-essay.fiddle-markdown :refer [Custom-markdown Fiddle-markdown-extensions]]
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.router4 :as r]))

(e/defn Essay [sitemap essay-md-folder]
  (e/client
    (dom/props {:class "Tutorial"})
    (dom/style (dom/text (e/server (some-> (io/resource "electric_essay/tutorial.css") slurp-safe))))
    (let [[?essay-filename & _] r/route]
      (if-not ?essay-filename
        (r/ReplaceState! ['. [(first (second (first sitemap)))]]) ; "two_clocks.md" encodes to /'two_clocks.md'
        (do
          #_(Consulting-banner)
          (if-some [md-content (e/server (slurp-safe (str essay-md-folder ?essay-filename ".md")))]
            (Custom-markdown (Fiddle-markdown-extensions) md-content)
            (do (dom/h1 (dom/text "Blog page not found: " ?essay-filename))
                (dom/p (dom/text "Probably we broke URLs, sorry! ")
                  (r/link ['. []] (dom/text "blog index"))))))))))
