(ns dustingetz.ui ; used in staffly.staff-detail
  (:require [contrib.css :refer [css-slugify]]
            [dustingetz.str :refer [pprint-str]]
            [dustingetz.combobox :refer [ComboBox]]
            #_[heroicons.electric3.v24.outline :as icons]
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            #_[hyperfiddle.ui.tags :as tag]))

(e/defn Identity [x] x)
(e/defn Text [x] (dom/text x))
(e/defn Default [x] (dom/text (pr-str x)))
(e/defn Ident [x] (dom/text (some-> x :db/ident name)))

#?(:cljs (defn rep [^js x] (.-rep x))) ; fix: Cannot infer target type in expression (. oo__11013__auto__ -rep)
(e/defn Rep [x] (Text (e/client (rep x)))) ; [TaggedValue: f, 8]

(e/defn EasyFormat [renderers x a]
  (e/client
    (let [Render (get renderers a Default)]
      (some-> (e/server (a x)) Render)))) ; inflexible, cell doesn't see whole record

(e/defn Debug [x]
  (dom/fieldset (dom/legend (dom/text "Debug"))
    (dom/pre (dom/props {:class "hyperfiddle-debug"})
      (dom/text (pprint-str x)))))

(e/defn EasyForm [x cols renders]
  (e/for [k (e/diff-by identity cols)]
    (dom/dt (dom/props {:class (css-slugify k)})
      (dom/text (name k)))
    (dom/dd (dom/props {:class (css-slugify k)})
      (let [Cell (get renders k Text)]
        (Cell (e/server (get x k)))))))

(e/defn ComboBox! [k v & {:as props}]
  (do (ComboBox v props) (e/amb)))

(e/defn TagPickerReadOnly [kf xs]
  (dom/props {:class "hyperfiddle-tags flex flex-cols flex-wrap gap-x-2 gap-y-1"}) ; TODO TagPicker
  (dom/text "todo tagpicker")
  #_
  (e/for [tag (e/diff-by kf xs)] ; Verify if `(into {} datomic-entity)` is enough
    (tag/tag
      (tag/content (dom/text (kf tag)))
      (tag/button (icons/x-mark (dom/props {:class "w-4"}))))))