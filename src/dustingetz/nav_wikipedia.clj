(ns dustingetz.nav-wikipedia
  "Wikipedia browser. Uses the Wikipedia REST API to search articles,
   view summaries, browse links between articles, and navigate categories.
   The navigation graph is infinite: every link and category is navigable."
  (:require
   [clojure.string :as str]
   [dustingetz.nav-util.http :as http]
   [dustingetz.nav-util.cache :as cache]
   [hyperfiddle.hfql2 :as hfql :refer [hfql]]
   [hyperfiddle.hfql2.protocols :refer [Identifiable Suggestable -hfql-resolve]])
  (:import
   [java.net URLEncoder]))

;; -- Records --

(defrecord WikiArticle  [title extract description thumbnail-url content-urls
                         page-id lang last-modified])
(defrecord WikiLink     [title ns])
(defrecord WikiCategory [title])
(defrecord WikiSearch   [title description thumbnail-url page-id])
(defrecord WikiSection  [id title level])
(defrecord ArticleRef   [title])
(defrecord CategoryRef  [title])

(def ^:private wiki-opts {:headers {"User-Agent" "HyperfiddleDemo/1.0 (hfql-demo)" "Accept" "application/json"}})

(defn- wiki-get
  "GET a Wikipedia REST API endpoint. Returns parsed JSON or nil."
  [path]
  (http/fetch-json (str "https://en.wikipedia.org" path) wiki-opts))

(defn- url-encode [s] (URLEncoder/encode (str s) "UTF-8"))

(defonce ^:private wiki-cache (cache/make-cache))

;; -- Data functions --

(defn wiki-search [query]
  (when-not (str/blank? query)
    (or (cache/cache-get wiki-cache [::search query] 300)
      (cache/cache-put! wiki-cache [::search query]
        (when-let [data (wiki-get (str "/api/rest_v1/page/related/" (url-encode query)))]
          (mapv (fn [p]
                  (->WikiSearch
                    (:title p)
                    (or (:description p) "")
                    (-> p :thumbnail :source)
                    (:pageid p)))
            (:pages data)))))))

(defn find-article [title]
  (or (cache/cache-get wiki-cache [::article title] 600)
    (cache/cache-put! wiki-cache [::article title]
      (when-let [data (wiki-get (str "/api/rest_v1/page/summary/" (url-encode title)))]
        (->WikiArticle
          (:title data)
          (:extract data)
          (:description data)
          (-> data :thumbnail :source)
          (-> data :content_urls :desktop :page)
          (:pageid data)
          (:lang data)
          (:timestamp data))))))

(defn article-links [title]
  (or (cache/cache-get wiki-cache [::links title] 600)
    (cache/cache-put! wiki-cache [::links title]
      (when-let [data (wiki-get (str "/api/rest_v1/page/related/" (url-encode title)))]
        (->> (:pages data)
          (mapv (fn [p] (->WikiLink (:title p) (:ns p))))
          (take 50)
          vec)))))

(defn article-categories [title]
  ;; Uses the MediaWiki action API for categories (not in REST API)
  (or (cache/cache-get wiki-cache [::categories title] 600)
    (cache/cache-put! wiki-cache [::categories title]
      (when-let [data (wiki-get
                        (str "/w/api.php?action=query&titles=" (url-encode title)
                          "&prop=categories&cllimit=50&format=json"))]
        (let [pages (-> data :query :pages)
              page (first (vals pages))]
          (->> (:categories page)
            (mapv (fn [c]
                    (->WikiCategory
                      (str/replace-first (:title c) #"^Category:" ""))))
            vec))))))

(defn category-members [cat-title]
  (or (cache/cache-get wiki-cache [::cat-members cat-title] 600)
    (cache/cache-put! wiki-cache [::cat-members cat-title]
      (when-let [data (wiki-get
                        (str "/w/api.php?action=query&list=categorymembers"
                          "&cmtitle=Category:" (url-encode cat-title)
                          "&cmlimit=50&format=json"))]
        (->> (-> data :query :categorymembers)
          (mapv (fn [m]
                  (->WikiSearch
                    (:title m)
                    ""
                    nil
                    (:pageid m))))
          vec)))))

(defn article-sections [title]
  (or (cache/cache-get wiki-cache [::sections title] 600)
    (cache/cache-put! wiki-cache [::sections title]
      (when-let [data (wiki-get (str "/api/rest_v1/page/mobile-sections/" (url-encode title)))]
        (->> (:remaining data)
          (mapv (fn [s]
                  (->WikiSection
                    (:id s)
                    (:line s)
                    (:toclevel s))))
          vec)))))

(defn featured-article []
  (or (cache/cache-get wiki-cache ::featured 3600)
    (cache/cache-put! wiki-cache ::featured
      (when-let [data (wiki-get "/api/rest_v1/page/random/summary")]
        (->WikiArticle
          (:title data)
          (:extract data)
          (:description data)
          (-> data :thumbnail :source)
          (-> data :content_urls :desktop :page)
          (:pageid data)
          (:lang data)
          (:timestamp data))))))

;; -- Sub-collection accessors (take a WikiArticle, return child collections) --

(defn article-related [^WikiArticle a]
  (article-links (:title a)))

(defn article-cats [^WikiArticle a]
  (article-categories (:title a)))

;; -- Named accessors --

(defn article-title [a] (->ArticleRef (:title a)))
(defn link-title [l] (->ArticleRef (:title l)))
(defn search-title [s] (->ArticleRef (:title s)))
(defn category-name [c] (->CategoryRef (:title c)))

;; -- Protocol extensions --

(extend-type ArticleRef
  Identifiable (-identify [r] `(find-article ~(:title r)))
  Suggestable (-suggest [_] (hfql [:title])))

(extend-type CategoryRef
  Identifiable (-identify [r] `(find-category ~(:title r)))
  Suggestable (-suggest [_] (hfql [:title])))

(extend-type WikiArticle
  Identifiable (-identify [a] `(find-article ~(:title a)))
  Suggestable (-suggest [_] (hfql [:title :extract :description :page-id :lang :last-modified])))

(extend-type WikiLink
  Identifiable (-identify [l] `(find-article ~(:title l)))
  Suggestable (-suggest [_] (hfql [:title :ns])))

(extend-type WikiCategory
  Identifiable (-identify [c] `(find-category ~(:title c)))
  Suggestable (-suggest [_] (hfql [:title])))

(extend-type WikiSearch
  Identifiable (-identify [s] `(find-article ~(:title s)))
  Suggestable (-suggest [_] (hfql [:title :description :page-id])))

(extend-type WikiSection
  Identifiable (-identify [s] `(find-article "Main_Page"))
  Suggestable (-suggest [_] (hfql [:id :title :level])))

;; -- Extra functions for navigation --

(defn find-category [title]
  ;; A category is just a list of its members
  (category-members title))

;; -- Sitemap --

(def sitemap
  {`find-article
   (hfql [:title :extract :description :page-id :lang :last-modified :content-urls
          {article-related {* ^{::hfql/select `find-article}
                            [^{::hfql/link `(find-article ~'%)} link-title :ns]}}
          {article-cats {* ^{::hfql/select `find-category}
                         [^{::hfql/link `(find-category ~'%)} category-name]}}])

   `article-links
   (hfql {(article-links %) {* ^{::hfql/select `find-article}
                             [^{::hfql/link `(find-article ~'%)} link-title :ns]}})

   `article-categories
   (hfql {(article-categories %) {* ^{::hfql/select `find-category}
                                  [^{::hfql/link `(find-category ~'%)} category-name]}})

   `category-members
   (hfql {(category-members %) {* ^{::hfql/select `find-article}
                                [^{::hfql/link `(find-article ~'%)} search-title
                                 :description]}})

   `article-sections
   (hfql {(article-sections %) {* [:title :level]}})

   `featured-article
   (hfql {(featured-article) [:title :extract :description :page-id
                              {article-related {* ^{::hfql/select `find-article}
                                                [^{::hfql/link `(find-article ~'%)} link-title :ns]}}
                              {article-cats {* ^{::hfql/select `find-category}
                                             [^{::hfql/link `(find-category ~'%)} category-name]}}]})})

;; -- Resolvers --

(defmethod -hfql-resolve `find-article [[_ title]] (find-article title))
(defmethod -hfql-resolve `article-links [[_ title]] (article-links title))
(defmethod -hfql-resolve `article-categories [[_ title]] (article-categories title))
(defmethod -hfql-resolve `category-members [[_ title]] (category-members title))
(defmethod -hfql-resolve `article-sections [[_ title]] (article-sections title))
(defmethod -hfql-resolve `featured-article [_] (featured-article))
(defmethod -hfql-resolve `find-category [[_ title]] (find-category title))
