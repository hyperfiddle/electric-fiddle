(ns datagrid.gitbrowser
  (:require #?(:clj [clj-jgit.porcelain :refer [load-repo]])
            [contrib.color]
            [clojure.datafy :refer [datafy]]
            [datagrid.datafy-git #?(:clj :as, :cljs :as-alias) git]
            [datagrid.datafy-renderer :as r]
            [datagrid.file-explorer :refer [RouterInput]]
            [datagrid.schema :as schema]
            [hyperfiddle.electric :as e]
            [hyperfiddle.electric-css :as css]
            [hyperfiddle.electric-dom2 :as dom]
            [hyperfiddle.router :as router]
            [clojure.string :as str]
            [datagrid.ui :as ui]))

(defmacro hfql [& body])

(e/def repo-path ".")
(e/def branches {})

(e/defn RenderDiffLink [V]
  (e/server
    (let [v (V.)]
      (e/client
        (router/link ['. :diff v]
          (dom/text v))))))

(e/defn RenderGitStat [type v]
  (e/server
    (let [v (V.)]
      (when-not (zero? v)
        (e/client
          (dom/props {:style {:color (case type ::additions :green, ::deletions :red :inherit)}})
          (dom/text (case type ::additions "+", ::deletions "-" "") v))))))

; {::git/path :string}
(e/defn ChangesList [commit]
  (dom/div (dom/props {:style {:display :flex, :flex-direction :column, :grid-row 3, :position :sticky, :top 0 :height "auto"}})
    (hfql
      {(props (::git/changes commit)
         {::r/header?       false
          ::r/row-height-px 25
          ::r/max-height-px "100%"
          ::dom/props       {:style {:grid-template-columns "auto min-content min-content"}}})
       [(props ::git/path {:render (RenderDiffLink. %)})
        (props ::git/additions {:render (RenderGitStat. hf/a %)})
        (props ::git/deletions {:render (RenderGitStat. hf/a %)})]})))

(e/defn DiffView [diff]
  (e/client
    (dom/div
      (dom/props {:class (css/scoped-style
                           (css/rule {:height :fit-content, :overflow-x :hidden, :grid-column 2, :grid-row 3})
                           (css/rule ".d2h-file-header" {:display :none}))})
      (let [config (js-obj "drawFileList" false
                     "fileListToggle" false
                     "fileListStartVisible" false
                     "fileContentToggle" false
                     "matching" "words" ; "lines"
                     "outputFormat" #_"line-by-line" "side-by-side"
                     "synchronisedScroll" true
                     "stickyFileHeaders" false
                     "highlight" true
                     "renderNothingWhenEmpty" false)
            ui     ^js (js/Diff2HtmlUI. dom/node diff config)]
        (.draw ui)))))

(e/defn RenderCommitId [props e a V]
  (e/server
    (let [commit-id (git/short-commit-id (V.))]
      (e/client
        (router/link ['. :details commit-id]
          (dom/props {:style {:font-family "monospace"}})
          (dom/text commit-id))))))

(e/defn RenderCommitMessage [props e a V]
  (e/server
    (let [[_ [_e _a V-1]] r/stack
          commit          (r/JoinValue. (V-1.))
          branches        (::git/branches commit)
          message         (V.)]
      (e/client
        (dom/props {:style {:display :flex
                            :gap     "0.25rem"}})
        (when (seq branches)
          (e/for [branch (map ui/format-branch branches)]
            (dom/span (dom/props {:style {:border           "2px white solid"
                                          :color            :white
                                          :border-radius    "7px"
                                          :padding          "0.125rem 0.5rem"
                                          :box-sizing       :border-box
                                          :font-size        "0.75rem"
                                          :font-family      "monospace, sans serif"
                                          :background-color (ui/branch-color branch)}})
                      (dom/text branch))))
        (dom/span
          (dom/text message))))))

(e/defn RenderCommitTime [props e a V]
  (e/server
    (let [inst (V.)]
      (e/client
        (dom/props {:title (ui/format-commit-time false inst)})
        (dom/text (ui/format-commit-time inst))))))

(e/defn CommitMetadata [commit]
  (e/client
    (dom/div (dom/props {:style {:grid-row 1, :grid-column "1 / 3"}})
             (e/server
               (binding [r/Render          r/SchemaRenderer
                         r/schema-registry (schema/registry {:id :string, :author :string, :time inst?, :email :string})
                         r/renderers  (assoc r/renderers :time RenderCommitTime)]
                 (r/RenderForm. {::r/row-height-px 25
                                 ::r/max-height-px (* 25 7)
                                 ::r/keys [:id :author :merge :time :email :message]}
                   nil nil (e/fn* [] commit)))))))

(e/defn CommitInfo  [commit]
  (e/server
    (e/client
      (dom/div
        (dom/props {:style {:border-top "2px lightgray solid"
                            :overflow   :auto
                            :position   :relative
                            :height :auto
                            :padding-bottom "2rem"
                            :display :grid
                            :grid-template-columns "auto 1fr"
                            :grid-area "details"}})

        (ui/ClosePanelButton. ['.. `(GitBrowser ~repo-path)])
        (e/server
          (CommitMetadata. commit)
          (ChangesList. commit) #_(::git/changes commit)
          (DiffView. #_(::git/diff commit)
            (let [diffs (git/diffs (:repo commit) (git/parent-commit (:raw commit)) (:raw commit) ::git/default)] ; move into datafy
              (get diffs (e/client (ffirst (get router/route :diff)))
                (get diffs (::git/path (first (::git/changes commit))))))))))))

(e/defn GitLog [repo branch]
  (e/client
    (RouterInput. {::dom/type        :search
                   ::dom/placeholder "Search for commits"
                   ::dom/style {:grid-area "search"}}
      :message)
    (dom/div
      (dom/props {:style {:max-height  "100%"
                          :overflow    :auto
                          :position    :relative
                          :grid-area   "log"}})
      (e/server
        (binding [r/Render          r/SchemaRenderer
                  r/schema-registry (schema/registry {:id      :string
                                                      :author  :string
                                                      :message :string
                                                      :time    inst?})
                  r/renderers       (assoc r/renderers :id RenderCommitId
                                      :time RenderCommitTime
                                      :message RenderCommitMessage)]
          (r/RenderGrid.
            {::r/row-height-px 25
             ::r/max-height-px "100%"
             ::r/columns       [{::r/attribute :id}
                                {::r/attribute :message}
                                {::r/attribute :author}
                                {::r/attribute :time, ::r/sortable true}]
             ::dom/props       {:style {:grid-template-columns "min-content auto min-content min-content"}}}
            nil nil
            (e/fn []
              (->> (r/Nav. (datafy repo) [:log :branch branch])
                (r/InputFilter. :message)
                (r/ColumnSort. (fn [column]
                                 (case column ; Account for clj-jgit naming inconsistencies
                                   :time #(get-in % [:author :date]) ; clj-git stores time in [:author :date] for the log, but in :time for the commit itself
                                   column)))))))))))

(defn sequence-refs-tree [branches]
  (->> branches
    (sort-by key)
    (reduce (fn [r [k v]]
              (let [segments (->> (str/split k #"/")
                               (map vector (range) (repeat k)))]
                (reduce conj r (concat (butlast segments) [(conj (last segments) v)]))))
      [])
    (map #(zipmap [::depth ::full-name ::name ::ref] %))
    (contrib.data/distinct-by (juxt ::depth ::name))))

(e/defn RenderRefName [{::keys [depth full-name ref]}]
  (e/server
    (let [ref? (some? ref)]
      (e/client
        (dom/text (apply str (repeat (dec depth) "  ")))
        (router/link ['. :branch full-name]
          (dom/props {:disabled (not ref?)})
          (dom/text v))))))

(defn branch-list [repo] (sequence-refs-tree (git/branch-list repo)))

; {::name :string}
(e/defn ListRefs [repo]
  (dom/div
   (dom/props {:class (css/scoped-style
                           (css/rule {:overflow :auto})
                           (css/rule "a[disabled=true]"
                             {:cursor :text, :color :initial, :text-decoration :none}))}) 
    (hfql
      {(props (branch-list repo)
         {::r/row-height-px 25
          ::r/max-height-px "100%"})
       [(RenderRefName. %)]})))

(e/defn GitBrowser [& [git-repo-path]]
  (e/client
    (dom/props {:style {:padding        "1rem"
                        :padding-bottom "0.5rem"
                        :margin         0
                        :box-sizing     :border-box
                        :overflow       :hidden
                        :height         "100dvh"}})
    (dom/div (dom/props {:class (ui/LayoutStyle. (contains? router/route :details))})
      (e/server
        (binding [repo-path (or git-repo-path ".")]
          (let [repo (load-repo repo-path)]
            (binding [branches (git/branch-list repo)]
              (ListRefs. branches)
              (GitLog. repo (e/client (or (ffirst (:branch router/route)) "HEAD")))
              (e/client
                (when-let [commit-id (:details router/route)]
                  (e/server (CommitInfo. (datafy (git/get-commit repo (ffirst commit-id))))))))))))))

