(ns tourme.firebase.helper
  (:require [re-frame.core :as rf]
            [tourme.logging :as l]
            [tourme.firebase.firebase-prtcl :as prtcl]
            [cljs.core.async :refer [>! <! chan put!]])
  (:require-macros [cljs.core.async :refer [go]]))

(defn dprn [& args] (apply (partial l/dprn :fs) args))