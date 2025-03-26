(ns dustingetz.unifhir1
  (:require [clojure.string :as str]
            [contrib.data :refer [clamp-left subgroup-by]]
            [contrib.template :refer [comptime-resource]]
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.electric-forms5 :refer [Input*]]
            [hyperfiddle.electric-scroll0 :refer [Scroll-window IndexRing]]
            [hyperfiddle.router4 :as router]))

#?(:clj (def data (comptime-resource "dustingetz/unifhir.edn")))

(def ehr-formats
  {"xml-emis" "Emis"
   "fhir-tie" "Tie"
   "fhir-tie-legacy" "Tie Legacy"
   "xml-systmone" "SystmOne"
   "soap-rio" "Rio"
   "fhir-cerner" "Cerner"
   "hl7-epic" "Epic"
   "no-op" "No-op"})

#?(:clj (defn query-mms [#_search] (Thread/sleep 200) data))

#?(:clj (defn mms-record-matches [% search]
          (or (empty? search)
            (and (:source-measurement-type %) (str/includes? (str/lower-case (:source-measurement-type %)) search))
            (and (:code %) (str/includes? (str/lower-case (:code %)) search))
            (and (:display %) (str/includes? (str/lower-case (:display %)) search)))))

(comment
  (def xs-indexed (group-by :ehr-format (query-mms "")))
  (def xs (get xs-indexed "xml-emis"))
  (count xs) := 60
  (nth xs 1)
  := {:updated-at #inst"2024-11-27T11:31:32.232-00:00",
      :override-ehrs [],
      :source-value-filter nil,
      :system "http://snomed.info/sct",
      :unit "mmHg",
      :value nil,
      :unit-factor 1.0,
      :min-value nil,
      :max-value nil,
      :omit-value false,
      :updated-by "system",
      :id 21,
      :is-excluded false,
      :code "163020007",
      :display "O/E - blood pressure reading",
      :source-measurement-type "blood_pressure",
      :ehr-format "xml-emis",
      :used-by-ehrs ["Integration Test: EMIS"]}
  (tap> xs-indexed)
  )


(e/defn TableScroll [record-count xs! Row]
  (e/client
    (dom/div (dom/props {:class "Viewport"})
      (let [row-height 48
            [offset limit] (Scroll-window row-height record-count dom/node {:overquery-factor 1})]
        (dom/table (dom/props {:style {:position "relative" :top (str (* offset row-height) "px")}})
          (e/for [i (IndexRing limit offset)] ; render all rows even with fewer elements
            (dom/tr (dom/props {:style {:--order (inc i)} :data-row-stripe (mod i 2)})
              #_(when-some [x (nth xs! i nil)])
              (Row (e/server (nth xs! i nil)))))) ; beware glitched nil pass through
        (dom/div (dom/props {:style {:height (str (clamp-left ; row count can exceed record count
                                                    (* row-height (- record-count limit)) 0) "px")}}))))))

(def unicode-no-entry-sign "\uD83D\uDEAB") ; 🚫
(def unicode-long-rightwards-arrow "⟶")
(def unicode-lower-right-pencil "✎")
(def unicode-wastebasket "\uD83D\uDDD1") ; 🗑

(e/defn Row-mm-record [{:keys [is-excluded source-measurement-type source-value-filter system code
                               display unit value min-value max-value omit-value unit-factor
                               override-ehrs used-by-ehrs ehr-format] :as ?x}]
  ; rendering nil rows helps with overquery - offscreen rows must have a height even past the end of the list
  (e/server
    (dom/td (when ?x (dom/text source-measurement-type)))

    (dom/td
      (when ?x
        (let [used-count (count used-by-ehrs)
              override-count (count override-ehrs)]
          (dom/div (if (pos? used-count) (dom/text used-count " usage") (dom/text "Unused"))) ; green
          (dom/div (when (pos? override-count) (dom/text #_", " override-count " custom")))))) ; blue

    (dom/td (when ?x (dom/text (if is-excluded unicode-no-entry-sign unicode-long-rightwards-arrow))))

    ; tooltip metadata form
    (dom/td
      (when ?x
        (dom/div (dom/text display))
        (dom/div (dom/text code) (dom/text " ") (dom/text system))))
    (dom/td
      (when ?x
        (dom/div
          #_(dom/text "Value: ")
          (dom/text (cond
                      omit-value "Omitted" ; text-emerald-600 font-bold
                      value (str value " (static)") ; text-emerald-600 font-bold
                      (not= 1 (or unit-factor 1)) (str "\u00D7" unit-factor " (factored)")
                      :else "Pass-through" ; text-gray-500 dark:text-gray-400 font-bold
                      ))
          #_(dom/text ", Unit: ") (when unit (dom/text " (" unit ")")))
        (dom/div
          (dom/text "Min: ") (dom/text (or min-value "-"))
          (dom/text " / Max: ") (dom/text (or max-value "-")))))
    (dom/td
      (when ?x
        (dom/text unicode-lower-right-pencil)
        (dom/text unicode-no-entry-sign)
        (dom/text unicode-wastebasket)))))

(e/defn Ehr-type-filters [xs-indexed!]
  (e/client
    (e/for [[k label] (e/diff-by key ehr-formats)]
      (router/link ['. [k]] (dom/text label "(" (e/server (count (get xs-indexed! k))) ")")))))

(declare css)
(e/defn Unifhir1 []
  (e/client
    (dom/style (dom/text css)) (dom/props {:class "Explorer Unifhir"})
    (let [!filters (atom {::search ""}) filters (e/watch !filters)
          [ehr-type] router/route]
      (if-not ehr-type
        (router/ReplaceState! ['. ["xml-emis"]])
        (let [xs-indexed! (e/server (->> (query-mms)
                                      (filter #(mms-record-matches % (::search filters)))
                                      (subgroup-by (juxt :ehr-format))))
              xs! (e/server (get xs-indexed! ehr-type))
              n (e/server (count xs!))]
          (dom/fieldset
            (dom/legend
              (dom/text "Measurement Maps (" (get ehr-formats ehr-type) ") ")
              (do (swap! !filters assoc ::search (Input* "")) (dom/text " (" n " items) "))
              (Ehr-type-filters xs-indexed!))
            (TableScroll n xs! Row-mm-record)))))))

(def css "
/* Scroll machinery */
.Explorer { position: fixed; } /* mobile: don't allow momentum scrolling on page */
.Explorer .Viewport { height: 100%; overflow-x:hidden; overflow-y:auto; }
.Explorer table { display: grid; }
.Explorer table tr { height: 48px; grid-row: var(--order); display: grid; grid-column: 1 / -1; grid-template-columns: subgrid; }

/* Cosmetic grid standard */
.Explorer fieldset { padding: 0; padding-left: 0.5em; background-color: white; }
.Explorer legend { margin-left: 1em; font-size: larger; }
.Explorer legend > input[type=text] { vertical-align: middle; }
.Explorer table td { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.Explorer table tr[data-row-stripe='0'] td { background-color: #f2f2f2; }
.Explorer table tr:hover td { background-color: #ddd; }

/* Userland layout */
.Explorer fieldset { position:fixed; top:0em; bottom:0; left:0; right:0; }
.Explorer table { grid-template-columns: 20em 5em 5em 20em 15em auto; }


.Unifhir legend a+a { margin-left: .25em; }
.Unifhir table td:nth-child(1) { font-weight: 600; }
.Unifhir dl { margin: 0; display: grid; grid-template-columns: max-content auto; }
.Unifhir dt { grid-column: 1; }
.Unifhir dd { grid-column: 2; margin-left: 1em; margin-bottom: .5em; }
")
