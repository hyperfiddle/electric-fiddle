(ns dustingetz.nav-util.cache
  "Simple in-memory TTL cache for nav files.
   Stores [value timestamp] tuples in an atom-backed map.")

(defn make-cache
  "Create a new TTL cache atom."
  []
  (atom {}))

(defn cache-get
  "Get a value from cache if it exists and hasn't expired.
   Returns the cached value or nil."
  [cache-atom k ttl-seconds]
  (when-let [[v ts] (get @cache-atom k)]
    (when (< (- (System/currentTimeMillis) ts) (* ttl-seconds 1000))
      v)))

(defn cache-put!
  "Put a value into the cache with current timestamp. Returns the value."
  [cache-atom k v]
  (swap! cache-atom assoc k [v (System/currentTimeMillis)])
  v)

(defn cached
  "Get-or-compute: return cached value if fresh, else call (f), cache, and return.
   f is a zero-arg function. Returns nil if f returns nil (does not cache nil)."
  [cache-atom k ttl-seconds f]
  (or (cache-get cache-atom k ttl-seconds)
      (when-let [v (f)]
        (cache-put! cache-atom k v))))
