(ns datagrid.fiddles
  (:require
   #?(:clj [clojure.java.io :as io])
   #?(:clj [contrib.str])
   #?(:clj [datagrid.parser :as parser])
   [clojure.edn :as edn]
   [clojure.string :as str]
   [datagrid.collection-editor :as ce]
   [datagrid.datagrid :as dg]
   [datagrid.spinner :as spinner]
   [datagrid.stage :as stage]
   [datagrid.virtual-scroll :as vs]
   [hyperfiddle.electric :as e]
   [hyperfiddle.electric-dom2 :as dom]
   [hyperfiddle.electric-ui4 :as ui]
   [hyperfiddle.incseq :as incseq])
  (:import [hyperfiddle.electric Pending]))

#?(:clj
   (defn read-hosts-file []
     (let [file (io/file "/etc/hosts")]
       (when (.exists file)
         (slurp file)))))


(defn diff [editor-state] (::ce/current-diff editor-state))
(defn patch [coll diff] (incseq/patch-vec coll diff))
;; (defn editor [hosts-ast] (ce/collection-editor (count hosts-ast)))

(defn zip-hosts [aliases hosts]
  (let [only-spaces (filter #(= :blank (first %)) aliases)
        hosts       (map #(vector :hostname %) hosts)
        num-spaces  (count only-spaces)]
    (concat (interleave (take num-spaces hosts) only-spaces) (interpose [:blank " "] (drop num-spaces hosts)))
    ))

(comment
  (zip-hosts [[:hostname "foo"] [:blank "  "] [:hostname "bar"] [:blank "  "]] ["baz" #_#_#_"asdf" "fdsa" "123"])
  := '([:hostname "baz"] [:blank "  "])
  (zip-hosts [[:hostname "foo"] [:blank "  "] [:hostname "bar"] [:blank "  "]] ["baz" "asdf" "fdsa" "123"])
  := '([:hostname "baz"] [:blank "  "] [:hostname "asdf"] [:blank "  "] [:hostname "fdsa"] [:blank " "] [:hostname "123"])


  (interleave [:a :b :c :d] [" " " "]))

(defn set-hosts [entry hosts] (update entry :aliases zip-hosts hosts))
(defn set-ip [entry ip] (assoc entry :ip ip))

#?(:clj
   (defn set-line! [editor index text]
     ((::ce/change! editor) index (parser/parse-line text))))

#?(:clj
   (defn toggle-entry [[type _value :as entry]]
     (case type
       :blank   entry
       :comment (parser/parse-line (str/replace (parser/serialize-line entry) #"^#\s+" ""))
       :entry   (parser/parse-line (str "# " (parser/serialize-line entry))))))

#?(:clj
   (defn toogle-entry! [editor coll index]
     (let [entry (get (patch coll (diff @(::ce/!state editor))) index)]
       ((::ce/change! editor) index (toggle-entry entry)))))

(comment
  (let [coll (parser/parse (slurp "/etc/hosts"))
        {::ce/keys [create! delete! change! rotate! !state] :as editor} (editor coll)]
    ;; (change! 1 [:comment "# hello"])
    ;; (set-line! editor 0 "::2 local2 localhost2")
    ;; (rotate! 0 1)
    (def _editor editor)
    (toogle-entry! editor coll 6)
    (toogle-entry! editor coll 6)
    ;; (create!)
    (change! 14 (parser/parse-line "::1 foo bar"))
    (print (parser/serialize (patch coll (diff @!state))))))


(defn find-input-horizontal [direction node]
  (let [step (case direction
               ::forwards  #(.-nextElementSibling %)
               ::backwards #(.-previousElementSibling %))
        cell (.closest node "td")]
    (if-let [next-input (loop [next-cell (step cell)]
                          (when next-cell
                            (if-let [next-input (.querySelector next-cell "input")]
                              next-input
                              (recur (step next-cell)))))]
      [::same-line next-input]
      (let [row (.closest cell "tr")]
        (loop [next-row (step row)]
          (when next-row
            (if-let [next-input (.querySelector next-row "input")]
              [::other-line next-input]
              (recur (step next-row)))))))))

(defn find-input-vertical [direction node]
  (let [cell    (.closest node "td")
        row     (.closest node "tr")
        y-index (max 0 (dec (count (take-while some? (iterate #(.-previousElementSibling %) cell)))))]
    (when-let [next-row (case direction
                          ::forwards  (.-nextElementSibling row)
                          ::backwards (.-previousElementSibling row))]
      (let [next-row-cells-count (.-childElementCount next-row)
            next-cell            (aget (.-childNodes next-row) (min next-row-cells-count y-index))]
        (when-let [next-input (.querySelector next-cell "input")]
          [::other-line next-input])))))

(defn find-next-input [axis direction node]
  (case axis
    ::horizontal (find-input-horizontal direction node)
    ::vertical   (find-input-vertical direction node)))

(def flip-axis {::horizontal ::vertical
                ::vertical   ::horizontal})

#?(:cljs
   (defn focus-next-input [axis direction node]
     (if-let [found (find-next-input axis direction node)]
       (let [[location next-input] found]
         (when (= ::other-line location)
           (set! (.-scrollLeft (.closest node ".virtual-scroll")) 0))
         (.scrollIntoView next-input #js{:block "nearest", :inline "nearest"})
         (.setTimeout js/window #(.focus next-input) 50))
       (if (find-next-input (flip-axis axis) direction node)
         (focus-next-input (flip-axis axis) direction node)
         (.blur node)))))

(e/defn* CellInput [value OnCommit]
  (e/client
    (stage/staged OnCommit
      (dom/input (dom/props {:type  :text
                             :style {:padding "1px 0"
                                     :width   "100%"
                                     :height  "100%"
                                     :border  :none
                                     :white-space :pre
                                     :font-family :monospace}})
                 (set! (.-value dom/node) value)
                 (dom/on "blur" (e/fn* [_] (when (some? stage/stage) (stage/Commit. stage/stage))))
                 (dom/on! "keydown" (fn [^js e]
                                      (case (.-key e)
                                        ("Enter" "Tab" "Escape") (.preventDefault e)
                                        nil)
                                      (let [direction (if (.-shiftKey e) ::backwards ::forwards)]
                                        (case (.-key e)
                                          "Enter"  (focus-next-input ::vertical direction dom/node)
                                          "Tab"    (focus-next-input ::horizontal direction dom/node)
                                          "Escape" (do (stage/discard!)
                                                       (set! (.-value dom/node) value)
                                                       (.focus (.closest dom/node "table")))
                                          nil))))
                 (dom/on! "input" (fn [^js e] (stage/stage! (.. e -target -value))))))))

(def SHADOW "0 1px 3px 0 rgb(0 0 0 / 0.1), 0 1px 2px -1px rgb(0 0 0 / 0.1);")

(e/def loading? false)

(e/defn RowChangeMonitor [{::keys [rows OnChange]} Body]
  (e/client
    (let [!loading? (atom false)]
      (binding [loading? (e/watch !loading?)]
        (e/server
          (let [!dirty      (atom false)
                output-rows (Body. rows)]
            (when (or (not= rows output-rows) (e/watch !dirty))
              (reset! !dirty true)
              (try (OnChange. output-rows)
                   ;; nil
                   (catch Pending _
                     (e/client
                       (reset! !loading? true)
                       (e/on-unmount #(reset! !loading? false))))))))))))

(e/defn HostsGrid [rows]
  (e/server
    (RowChangeMonitor. {::rows     rows
                        ::OnChange (e/fn* [rows] rows)}
      (e/fn* [rows]
        (e/client
          (vs/virtual-scroll {::vs/row-height  30
                              ::vs/padding-top 30
                              ::vs/rows-count  (e/server (count rows))}
            (dom/props {:style {:max-height "330px"}})
            (dg/datagrid {::dg/row-height 30}
              (dom/props {:style    {:grid-auto-columns "auto"
                                     ;; :width             "100%"
                                     :border-collapse   :collapse}
                          :tabIndex "1"})
              #_(dom/on "keydown" (e/fn* [^js e] ; undo/redo (Ctrl-z / Shift-Ctrl-z)
                                    (when (and (or (.-ctrlKey e) (.-metaKey e)) (= "z" (.-key e)))
                                      (if (.-shiftKey e)
                                        (e/server (redo!))
                                        (e/server (undo!))))))
              (dom/element :style
                (dom/text (str "#" dg/id " td:focus-within {outline: 1px lightgray solid;}")
                          (str "#" dg/id " tr td {background-color: white}")
                          (str "#" dg/id " tr.header td > * {background-color: lightgray}")))
              (dg/header
                (dg/row
                  (dom/props {:style {:box-shadow SHADOW
                                      :z-index    10}})
                  (dg/column {::dg/frozen? true}
                    (dom/props {:style {:font-size        "0.75rem"
                                        :line-height      "1rem"
                                        :display          :flex
                                        :align-items      :center
                                        :justify-content  :center
                                        :background-color "#EFEFEF"
                                        :height           "30px"
                                        :grid-column      1
                                        :min-width        "6ch"
                                        :width            :min-content
                                        :resize           :none}})
                    (when (or loading? dg/loading?)
                      (spinner/Spinner. {})))
                  (dg/column {::dg/frozen? true}
                    (dom/props {:style {:font-size        "0.75rem"
                                        :line-height      "1rem"
                                        :display          :flex
                                        :align-items      :center
                                        :justify-content  :center
                                        :background-color "#EFEFEF"
                                        :height           "30px"
                                        :grid-column      2
                                        :min-width        "3ch"
                                        :width            :min-content
                                        :resize           :none}})
                    )
                  (e/for-by second [[idx column] (map-indexed vector ["Entry"])]
                    (dg/column {}
                      (dom/props {:style {:padding          "0 1rem"
                                          :resize           :horizontal
                                          :background-color "#EFEFEF"
                                          :height           "30px"
                                          :grid-column      (+ 3 idx)}})
                      (dom/text column)))))
              (e/server
                (let [indexed-rows (->> rows
                                     (map-indexed vector)
                                     ;; (filter #(= :entry (first (second %))))
                                     (vec))
                      {::ce/keys [rows change!]} (ce/CollectionEditor. indexed-rows)]
                  (vs/ServerSidePagination. rows
                    (e/fn* [[idx row]]
                      (let [row-number vs/row-number]
                        (e/client
                          (dg/row
                            (dg/cell (dom/props {:style     {:font-variant-numeric "tabular-nums"
                                                             :border               "1px #E1E1E1 solid"
                                                             :font-size            "0.75rem"
                                                             :line-height          "1rem"
                                                             :display              :flex
                                                             :align-items          :center
                                                             :justify-content      :center}
                                                 :draggable false #_editable?})
                                     (dom/text (inc row-number))
                                     #_(when editable?
                                         (dom/on! "contextmenu" (fn [event] (cm/open! {:row-number row-number} event)))
                                         (dom/on! "dragstart" (fn [^js e]
                                                                (set! (.. e -dataTransfer -dropEffect) "move")
                                                                (.. e -dataTransfer (setDragImage (.. e -target (closest "tr")) 0 0))
                                                                (.. e -dataTransfer (setData "text/plain" row-number))))
                                         (dom/on! "dragover" (fn [^js e] (.preventDefault e))) ; recommended by MDN
                                         (dom/on "drop" (e/fn* [^js e] (let [from (js/parseInt (.. e -dataTransfer (getData "text/plain")))]
                                                                         (e/server (rotate! from row-number)))))))
                            (dg/cell (dom/props {:style     {:border               "1px #E1E1E1 solid"
                                                             :font-size            "0.75rem"
                                                             :line-height          "1rem"
                                                             :display              :flex
                                                             :align-items          :center
                                                             :justify-content      :center}})
                                     (ui/checkbox (= :entry (first row))
                                         (e/fn* [checked?]
                                           ;; (e/server (prn "checked?" checked? (toggle-entry row)))
                                           (e/server (change! idx [idx (toggle-entry (e/snapshot row))])))
                                         (dom/props {:checked (= :entry (first row))})))
                            (e/for-by identity [column ["Entry"]]
                              (dg/cell
                                (dom/props {:class "focus-within:border-blue-600" ;; TODO migrate
                                            :style {:display       :block
                                                    :overflow      :hidden
                                                    :text-overflow :ellipsis
                                                    :white-space   :nowrap
                                                    :border        "1px #E1E1E1 solid"}})
                                (if true #_editable?
                                    (CellInput. (e/server (parser/serialize-line row))
                                      (e/fn* [new-value]
                                        (e/server
                                          (change! idx [idx (parser/parse-line new-value)]))))
                                    (dom/span (dom/props {:style {:padding "1px 0"
                                                                  :width   "100%"
                                                                  :height  "100%"
                                                                  :border  :none}})
                                              (dom/text (e/server (parser/serialize-line row))))))))))))
                  (map second rows))))))))))

(defn reset!* [atom value]
  (when value
    (reset! atom value)))

(e/defn HostFile-Editor []
  (e/client
    (dom/h1 (dom/text "/etc/hosts editor"))
    (let [!hosts (atom (e/server (read-hosts-file)))
          hosts  (e/watch !hosts)
          ast    (e/server (parser/parse hosts))]
      (dom/div (dom/props {:style {:display :grid, :grid-template-columns "1fr 1fr"
                                   :width   "100%"}})
               (dom/div (dom/props {:style {:grid-column "1/3"}})
                        (e/server
                          (when-let [ast (HostsGrid. ast)]
                            (let [hosts (parser/serialize ast)]
                              (e/client (reset!* !hosts hosts))))))
               (dom/textarea (dom/props {:style {:overflow    :scroll
                                                 :grid-column 1
                                                 :grid-row    2}})
                             (dom/text hosts)
                             (dom/on! "keyup" (fn [^js e]
                                                (reset! !hosts (.. e -target -value)))))
               (dom/textarea (dom/props {:style {:overflow    :scroll
                                                 :grid-column 2
                                                 :grid-row    2}
                                         :rows  25})
                        (dom/text (contrib.str/pprint-str ast))
                        (dom/on "keyup" (e/fn [^js e]
                                          (let [ast-text (.. e -target -value)]
                                            (e/server
                                              (try
                                                (let [hosts (parser/serialize (edn/read-string ast-text))]
                                                  (e/client (reset! !hosts hosts)))
                                                (catch Throwable t
                                                  (let [m (ex-message t)]
                                                    (e/client (prn m))))))))))))))

;; Dev entrypoint
;; Entries will be listed on the dev index page (http://localhost:8080)
(e/def fiddles {`HostsFile-Editor HostFile-Editor})

;; Prod entrypoint, called by `prod.clj`
(e/defn FiddleMain [ring-request]
  (e/server
    (binding [e/http-request ring-request] ; make ring request available through the app
      (e/client
        (binding [dom/node js/document.body] ; where to mount dom elements
          (HostFile-Editor.))))))
