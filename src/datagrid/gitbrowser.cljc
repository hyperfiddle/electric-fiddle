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

(e/def branches {})

;; TODO merge
;; {::git/path      :string
;;  ::git/additions :number
;;  ::git/deletions :number
;;  ::git/changes   [:sequential ::git/change]
;;  ::git/change    [:map [::git/path ::git/additions ::git/deletions]]}

;; TODO merge
;; (css/rule ".datafy_datafy-git/changes" {:display :flex, :flex-direction :column, :grid-row 3, :position :sticky, :top 0 :height "auto"})
;; (css/rule ".datafy_datafy-git/changes .datafy_datafy-git/additions" {:color :green})
;; (css/rule ".datafy_datafy-git/changes .datafy_datafy-git/additions:before" {:content "+"})
;; (css/rule ".datafy_datafy-git/changes .datafy_datafy-git/deletions" {:color :red})
;; (css/rule ".datafy_datafy-git/changes .datafy_datafy-git/deletions:before" {:content "-"})

(e/defn ChangesList [!commit]
  (hfql
    {(props (::git/changes !commit)
       {::r/header?       false
        ::r/row-height-px 25
        ::r/max-height-px "100%"
        ::dom/props       {:style {:grid-template-columns "auto min-content min-content"}}})
     [(props ::git/path {:link [:diff %]})
      ::git/additions
      ::git/deletions]}))

;; TODO merge
;; (css/rule ".diff-view" {:height :fit-content, :overflow-x :hidden, :grid-column 2, :grid-row 3})
;; (css/rule ".diff-view .d2h-file-header" {:display :none})

(e/defn DiffView [diff]
  (e/client
    (dom/div (dom/props {:class "diff-view"})
      (->> (js-obj
             "drawFileList"           false
             "fileListToggle"         false
             "fileListStartVisible"   false
             "fileContentToggle"      false
             "matching"               "words" ; "lines"
             "outputFormat"           "side-by-side" ; "line-by-line"
             "synchronisedScroll"     true
             "stickyFileHeaders"      false
             "highlight"              true
             "renderNothingWhenEmpty" false)
        (js/Diff2HtmlUI. dom/node diff)
        (.draw)))))

(declare format-relative-time format-absolute-time)

#_{:id :string, :author :string, :time inst?, :email :string}
(e/defn CommitMetadata [!commit]
  (e/client
    (dom/div (dom/props {:style {:grid-row 1, :grid-column "1 / 3"}})

      (hfql {(props !commit
               {::r/row-height-px 25
                ::r/max-height-px (* 25 7)})
             [:id
              :author
              :merge
              (props :time {:render  (format-relative-time %)
                            :tooltip (format-absolute-time %)})
              :email
              :message]})

      (e/server
        (binding [r/Render          r/SchemaRenderer
                  r/schema-registry (schema/registry)
                  r/renderers  (assoc r/renderers :time RenderCommitTime)]
          (r/RenderForm. {::r/keys [:id :author :merge :time :email :message]}
            nil nil (e/fn* [] commit)))))))

;; TODO merge
;; (css/rule ".commit-info" {:border-top            "2px lightgray solid"
;;                           :overflow              :auto
;;                           :position              :relative
;;                           :height                :auto
;;                           :padding-bottom        "2rem"
;;                           :display               :grid
;;                           :grid-template-columns "auto 1fr"
;;                           :grid-area             "details"})

(e/defn CommitInfo  [commit]
  )

(defn needle-matches [keyfn needle collection] (filter #(str/includes? (keyfn %) needle) collection))

;; TODO merge
;; [:=> [:cat :string :string :string] [:sequential :commit]]
(e/defn Git-log [repo branch needle]
  (->> (hf/Nav. (datafy repo) [:log :branch branch])
       (needle-match :message needle)))

;; TODO merge
;; {:id      :string
;;  :author  :string
;;  :message :string
;;  :time    inst?}
;; (css/rule "input.git-log/needle" {:grid-area "search"})
;; (css/rule ".git-log" {:grid-area             "log"
;;                       :grid-template-columns "min-content auto min-content min-content"
;;                       :position              :relative
;;                       :overflow              :auto
;;                       :max-height            "100%"})

;; (css/rule ".git-log .message" {:display :flex, :gap "0.25rem"})
;; (css/rule ".git-log .message .branch-tag" {:border        "2px white solid"
;;                                            :color         :white
;;                                            :border-radius "7px"
;;                                            :padding       "0.125rem 0.5rem"
;;                                            :box-sizing    :border-box
;;                                            :font-size     "0.75rem"
;;                                            :font-family   "monospace, sans serif"})


;; FIXME refactor to hfql renderer
(e/defn RenderCommitMessage [^JCommit commit]
  (e/server
    (let [{:keys [::git/branches :message]} (datafy commit)]
      (e/client
        (e/for [branch branches]
          (let [branch (ui/format-branch branch)]
            (dom/span (dom/props {:class "branch-tag", :style {:background-color (ui/branch-color branch)}})
              (dom/text branch))))
        (dom/span
          (dom/text message))))))

(e/defn GitLog [!repo branch]
  (dom/div (dom/props {:class "log-wrapper"})
    (hfql {(props (Git-log. !repo branch (props . {:placeholder "Search for commits"}))
             {::r/row-height-px 25
              ::r/max-height-px "100%"})
           [(props :id {:link  [:details (git/short-commit-id %)]
                        :style {:font-family "monospace"}})
            (props :message {:render (RenderCommitMessage. %%)})
            :author
            (props :time {:sortable true,
                          :render  (format-relative-time %)
                          :tooltip (format-absolute-time %)})]})))

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

(e/defn RenderRefName [{::keys [depth name full-name ref]}]
  (e/client
    (dom/text (apply str (repeat (dec depth) "  ")))
    (router/link ['. :branch full-name]
      (dom/props {:disabled (e/server (not (some? ref)))})
      (dom/text name))))

(defn branch-list [repo] (sequence-refs-tree (git/branch-list repo)))

;; TODO merge
;; (css/rule ".branch-list" {:overflow :auto})
;; (css/rule ".branch-list a[disabled=true]" {:cursor :text, :color :initial, :text-decoration :none})

(e/defn ListRefs [!repo]
  (hfql
    {(props (branch-list !repo)
       {::r/row-height-px 25
        ::r/max-height-px "100%"})
     [(RenderRefName. %)]}))

(e/defn GitBrowser [& [git-repo-path]]
  (e/client
    (dom/props {:style {:padding        "1rem"
                        :padding-bottom "0.5rem"
                        :margin         0
                        :box-sizing     :border-box
                        :overflow       :hidden
                        :height         "100dvh"}}) ; electric-fiddle integration, doesn't count
    (dom/div (dom/props {:class (ui/LayoutStyle. (contains? router/route :details))})
      (e/server
        (let [commit-id (e/client (ffirst (:details router/route)))
              branch (e/client (or (ffirst (:branch router/route)) "HEAD"))]

          (hfql
            {(load-repo (or git-repo-path "."))
             [(ListRefs. %)
              (GitLog. % branch)
              {(git/get-commit % commit-id)
               [(CommitMetadata. %)
                (ChangesList. %)
                (DiffView. #_(::git/diff commit)
                  (let [commit %
                        diffs (git/diffs (:repo commit) (git/parent-commit (:raw commit)) (:raw commit) ::git/default)] ; move into datafy
                    (get diffs (e/client (ffirst (get router/route :diff)))
                      (get diffs (::git/path (first (::git/changes commit)))))))]}]}))))))
