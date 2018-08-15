(ns tourme.config)

(cljs.core/goog-define INTERNALTEST false)

(def debug-position? (and goog.DEBUG false))
(def debug-firebase? (and goog.DEBUG true))
(def debug-firestore? (and goog.DEBUG true))
(def debug-permissions? (and goog.DEBUG true))
(def mock-http? (and goog.DEBUG false))
(def mock-firebase? (and goog.DEBUG false))

(def debug-db-set :default)

(def max-chars-name 30) ;;follow tour max 37
(def max-chars-loc-description 234)