(ns electric-starter-app.main
  (:require [hyperfiddle.electric3 :as e]
            [electric-tutorial.forms3a-form]
            [hyperfiddle.electric-dom3 :as dom]))

(e/defn Main [ring-request]
  (e/client
    (binding [dom/node js/document.body]
      (dom/link (dom/props {:rel :stylesheet, :href "/tutorial.css"}))
      (electric-tutorial.forms3a-form/Forms3a-form))))
