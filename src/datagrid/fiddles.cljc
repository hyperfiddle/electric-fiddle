(ns datagrid.fiddles
  (:require
   #?(:clj [clojure.java.io :as io])
   #?(:clj [contrib.str])
   #?(:clj [datagrid.writer])
   [clojure.string :as str]
   [datagrid.collection-editor :as ce]
   [datagrid.context-menu :as cm]
   [datagrid.datagrid :as dg]
   [datagrid.parser :as parser]
   [datagrid.spinner :as spinner]
   [datagrid.stage :as stage]
   [datagrid.virtual-scroll :as vs]
   [heroicons.electric.v24.outline :as icons]
   [hyperfiddle.electric :as e]
   [hyperfiddle.electric-css :as css]
   [hyperfiddle.electric-dom2 :as dom]
   [hyperfiddle.electric-ui4 :as ui]
   [hyperfiddle.incseq :as incseq])
  (:import [hyperfiddle.electric Pending]))

#?(:clj
   (defn read-hosts-file []
     (let [file (io/file "/etc/hosts")]
       (when (.exists file)
         (slurp file)))))

#?(:clj
   (defn save-hosts-file! [content-str]
     (when content-str
       (datagrid.writer/write-hosts-file! content-str))))

(defn diff [editor-state] (::ce/current-diff editor-state))
(defn patch [coll diff] (incseq/patch-vec coll diff))

(defn toggle-entry [[type _value :as entry]]
  (case type
    :blank           entry
    :comment         entry
    :commented-entry (parser/parse-line (str/replace (parser/serialize-line entry) #"^#\s+" ""))
    :entry           (parser/parse-line (str "# " (parser/serialize-line entry)))))

(e/defn* CellsStyle []
  (e/client
    (css/scoped-style
      (css/rule ".cell-input"
        {:padding     "1px 0"
         :width       "100%"
         :height      "100%"
         :border      :none
         :white-space :pre
         :font-family :monospace})
      (css/rule ".number-cell"
        {:font-variant-numeric "tabular-nums"
         :border               "1px #E1E1E1 solid"
         :font-size            "0.75rem"
         :line-height          "1rem"
         :display              :flex
         :align-items          :center
         :justify-content      :center})
      (css/rule ".checkbox-cell"
        {:border          "1px #E1E1E1 solid"
         :font-size       "0.75rem"
         :line-height     "1rem"
         :display         :flex
         :align-items     :center
         :justify-content :center})
      (css/rule ".entry-cell"
        {:display       :block
         :overflow      :hidden
         :text-overflow :ellipsis
         :white-space   :nowrap
         :border        "1px #E1E1E1 solid"})
      (css/rule ".entry-cell:focus-within"
        {:border-color "rgb(37 99 235)" #_ "border-blue-600"}))))

(e/defn* CellInput [value OnCommit]
  (e/client
    (stage/staged OnCommit
      (dom/input (dom/props {:type :text :class "cell-input"})
                 (set! (.-value dom/node) value)
                 (dom/on "blur" (e/fn* [_] (when (some? stage/stage) (stage/Commit. stage/stage))))
                 (dom/on! "keydown" (fn [^js e]
                                      (case (.-key e)
                                        ("Enter" "Tab" "Escape") (.preventDefault e)
                                        nil)
                                      (let [direction (if (.-shiftKey e) ::backwards ::forwards)]
                                        (case (.-key e)
                                          "Enter"  (dg/focus-next-input ::vertical direction dom/node)
                                          "Tab"    (dg/focus-next-input ::horizontal direction dom/node)
                                          "Escape" (do (stage/discard!)
                                                       (set! (.-value dom/node) value)
                                                       (.focus (.closest dom/node "table")))
                                          nil))))
                 (dom/on! "input" (fn [^js e] (stage/stage! (.. e -target -value))))))))

(e/def loading? false)

(e/defn RowChangeMonitor [{::keys [rows OnChange]} Body]
  (e/client
    (let [!loading? (atom false)]
      (binding [loading? (e/watch !loading?)]
        (e/server
          (let [output-rows (Body. rows)]
            (when (or (not= rows output-rows))
              (try (OnChange. output-rows)
                   nil
                   (catch Pending _
                     (e/client
                       (reset! !loading? true)
                       (e/on-unmount #(reset! !loading? false))))))))))))

(e/def IconStyle
  "Return a unique, generated class name to apply to multiple icons."
  (e/client
    (e/singleton
      (css/scoped-style
        (css/rule {:width "1rem", :height "1rem"})))))

(e/defn* PlusUpIcon []
  (e/client
    (dom/div
      (dom/props {:class (IconStyle.)
                  :style {:position :relative}})
      (icons/chevron-up (dom/props {:class (IconStyle.)
                                    :style {:position :absolute
                                            :top   "-0.45rem"}}))
      (icons/plus (dom/props {:class (IconStyle.)
                              :style {:position  :absolute
                                      :transform "scale(0.8)"}})))))

(e/defn* PlusDownIcon []
  (e/client
    (dom/div
      (dom/props {:class (IconStyle.)
                  :style {:position :relative}})
      (icons/chevron-down (dom/props {:class (IconStyle.)
                                      :style {:position :absolute
                                              :bottom   "-0.45rem"}}))
      (icons/plus (dom/props {:class (IconStyle.)
                              :style {:position  :absolute
                                      :transform "scale(0.8)"}})))))

(def SHADOW "0 1px 3px 0 rgb(0 0 0 / 0.1), 0 1px 2px -1px rgb(0 0 0 / 0.1)")
(def SHADOW-LG "0 10px 15px -3px rgb(0 0 0 / 0.1), 0 4px 6px -4px rgb(0 0 0 / 0.1)")

(e/defn* MenuStyle []
  (e/client
    (css/style
      (css/rule ".datagrid_fiddles__menu-items"
        {:z-index          10
         :box-shadow       SHADOW-LG
         :border-radius    "0.25rem"
         :display          :flex
         :flex-direction   :column
         :gap              "1px"
         :background-color "rgb(244, 244, 245)" ; bg-gray-100
         :border           "1px rgb(249, 250, 251) solid" ; border-gray-50
         })
      (css/rule ".datagrid_fiddles__menu-item"
        {:padding          "0.75rem 0.5rem"
         :cursor           :pointer
         :background-color :white
         :display          :flex
         :gap              "0.5rem"
         :align-items      :center})
      (css/rule ".datagrid_fiddles__menu-item:hover"
        {:background-color "rgb(243 244 246)" #_ "bg-gray-100"}))))

(e/defn Menu [create! delete! Body]
  (e/client
    (cm/menu {::cm/context-menu? true}
      (MenuStyle.)
      (dom/on! "click" cm/close!)
      (dom/on! js/window "keydown" cm/close!)
      (cm/items
        (dom/props {:class "datagrid_fiddles__menu-items"})
        (let [row-number (:row-number cm/context)]
          (cm/item
            (dom/props {:class "datagrid_fiddles__menu-item"})
            (PlusUpIcon.)
            (dom/text "Insert row above")
            (dom/on! "click" (fn [_] (create! (dec row-number) [(dec row-number) [:blank ""]]))))
          (cm/item
            (dom/props {:class "datagrid_fiddles__menu-item"})
            (icons/x-mark (dom/props {:style {:width "1rem", :height "1rem"}}))
            (dom/text "Delete row")
            (dom/on! "click" (fn [_] (delete! row-number))))
          (cm/item
            (dom/props {:class "datagrid_fiddles__menu-item"})
            (PlusDownIcon.)
            (dom/text "Insert row below")
            (dom/on! "click" (fn [_] (create! row-number [row-number [:blank ""]]))))))
      (Body.))))

(e/defn HostsGrid [rows OnChange]
  (e/server
    (RowChangeMonitor. {::rows rows, ::OnChange OnChange}
      (e/fn* [rows]
        (e/client
          (let [indexed-rows                                               (vec (map-indexed vector rows))
                {::ce/keys [rows change! create! delete! #_undo! #_redo!]} (ce/CollectionEditor. indexed-rows)
                rows                                                       (map-indexed (fn [idx row] (or row [idx [:blank ""]])) rows)]
           (Menu. create! delete!
             (e/fn* []
               (vs/virtual-scroll {::vs/row-height  30
                                   ::vs/padding-top 30
                                   ::vs/rows-count  (e/server (count rows))}
                 (dom/props {:style {:max-height (str (* 15 30) "px")}})
                 (dg/datagrid {::dg/row-height 30}
                   (dom/props {:tabIndex "1"})
                   #_(dom/on "keydown" (e/fn* [^js e] ; undo/redo (Ctrl-z / Shift-Ctrl-z)
                                         (when (and (or (.-ctrlKey e) (.-metaKey e)) (= "z" (.-key e)))
                                           (if (.-shiftKey e)
                                             (redo!)
                                             (undo!)))))
                   (dom/props {:class (css/scoped-style
                                        (css/rule {:grid-auto-columns "auto", :border-collapse :collapse})
                                        (css/rule "td:focus-within" {:outline "1px lightgray solid"})
                                        (css/rule "tr td" {:background-color :white})
                                        (css/rule "tr.header td > *" {:background-color :lightgray}))})
                   (dg/header
                     (dg/row
                       (dom/props {:style {:box-shadow SHADOW :z-index 10}
                                   :class (css/scoped-style
                                            (css/rule "th" {:font-size        "0.75rem"
                                                            :line-height      "1rem"
                                                            :display          :flex
                                                            :align-items      :center
                                                            :justify-content  :center
                                                            :background-color "#EFEFEF"
                                                            :height           "30px"}))})
                       (dg/column {::dg/frozen? true}
                         (dom/props {:style {:grid-column 1, :min-width "6ch", :width :min-content}})
                         (when (or loading? dg/loading?)
                           (spinner/Spinner. {})))
                       (dg/column {::dg/frozen? true}
                         (dom/props {:style {:grid-column 2, :min-width "3ch", :width :min-content}}))
                       (dg/column {}
                         (dom/props {:style {:font-size "1rem" :grid-column 3, :padding "0 1rem", :resize :horizontal}})
                         (dom/text "Entry"))))
                   (dom/props {:class (CellsStyle.)})
                   (vs/Paginate. rows
                     (e/fn* [[idx row]]
                       (let [row-number vs/row-number]
                         (e/client
                           (dg/row
                             (dg/cell (dom/props {:class     "number-cell"
                                                  :draggable false #_editable?})
                                      (dom/text (inc row-number))
                                      (when true #_editable?
                                            (dom/on! "contextmenu" (fn [event] (cm/open! {:row-number row-number} event)))
                                            #_(dom/on! "dragstart" (fn [^js e]
                                                                     (set! (.. e -dataTransfer -dropEffect) "move")
                                                                     (.. e -dataTransfer (setDragImage (.. e -target (closest "tr")) 0 0))
                                                                     (.. e -dataTransfer (setData "text/plain" row-number))))
                                            #_(dom/on! "dragover" (fn [^js e] (.preventDefault e))) ; recommended by MDN
                                            #_(dom/on "drop" (e/fn* [^js e] (let [from (js/parseInt (.. e -dataTransfer (getData "text/plain")))]
                                                                              (e/server (rotate! from row-number)))))))
                             (dg/cell (dom/props {:class "checkbox-cell"})
                                      (when (#{:entry :commented-entry} (first row))
                                        (ui/checkbox (= :entry (first row))
                                            (e/fn* [checked?]
                                              (change! idx [idx (toggle-entry (e/snapshot row))]))
                                          (dom/props {:checked (= :entry (first row))}))))
                             (e/for-by identity [column ["Entry"]]
                               (dg/cell
                                 (dom/props {:class "entry-cell"})
                                 (if true #_editable?
                                     (CellInput. (parser/serialize-line row)
                                       (e/fn* [new-value]
                                         (change! idx [idx (parser/parse-line new-value)])))
                                     (dom/span (dom/props {:style {:padding "1px 0"
                                                                   :width   "100%"
                                                                   :height  "100%"
                                                                   :border  :none}})
                                               (dom/text (parser/serialize-line row)))))))))))
                   (map second rows)))))))))))

(e/defn HostFile-Editor []
  (e/client
    (dom/h1 (dom/text "/etc/hosts editor"))
    (let [!hosts (atom (e/server (read-hosts-file)))
          hosts  (e/watch !hosts)
          ast    (parser/parse hosts)]
      (dom/div (dom/props {:style {:display :grid, :grid-template-columns "1fr 1fr"
                                   :width   "100%"}})
               (dom/div (dom/props {:style {:grid-column "1/3"}})
                        (e/server
                          (try
                            (HostsGrid. ast
                              (e/fn* [edited-ast]
                                (let [content-str (parser/serialize edited-ast)]
                                  (case (e/offload-task #(save-hosts-file! content-str))
                                    (e/client (reset! !hosts (e/server (read-hosts-file))))))))
                            (catch Pending _))))))))

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

(comment
  (defn zip-hosts [aliases hosts]
    (let [only-spaces (filter #(= :blank (first %)) aliases)
          hosts       (map #(vector :hostname %) hosts)
          num-spaces  (count only-spaces)]
      (concat (interleave (take num-spaces hosts) only-spaces) (interpose [:blank " "] (drop num-spaces hosts)))
      ))

  (zip-hosts [[:hostname "foo"] [:blank "  "] [:hostname "bar"] [:blank "  "]] ["baz" #_#_#_"asdf" "fdsa" "123"])
  := '([:hostname "baz"] [:blank "  "])
  (zip-hosts [[:hostname "foo"] [:blank "  "] [:hostname "bar"] [:blank "  "]] ["baz" "asdf" "fdsa" "123"])
  := '([:hostname "baz"] [:blank "  "] [:hostname "asdf"] [:blank "  "] [:hostname "fdsa"] [:blank " "] [:hostname "123"])

  (interleave [:a :b :c :d] [" " " "]))

(comment
  (defn toogle-entry! [editor coll index]
    (let [entry (get (patch coll (diff @(::ce/!state editor))) index)]
      ((::ce/change! editor) index (toggle-entry entry))))

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
