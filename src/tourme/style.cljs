(ns tourme.style
  (:require [reagent.core :as r]
            [re-frame.core :as rf]
            [tourme.events]))

(def absolute-filling-object {:position "absolute" :left 0 :right 0 :top 0 :bottom 0})

(def prim :white)
(def prim-d "#cccccc")

(def p-light "#ffffff")
(def p-dark "#c2c2c2")

(def light-gray "#888888")

(def sec "rgba(0, 167, 255, 1)")
(def sec-l "#69d8ff")
(def sec-d "#0079cb")

(def s-light "#e7ff8c")
(def s-dark "#7ecb20")

(def logo-image (js/require "./images/logo.png"))

(def color-disabled "rgba(216,216,216,1)")

(def message-font {:color light-gray :font-family "Helvetica Neue, Helvetica, Arial, sans-serif"})
(def font-heading1 {:color sec :font-family "RobotoSlab-Bold"})
(def font-heading2 {:color :black :font-family "OpenSans-Bold"})
(def font-explanation {:color sec :font-family "OpenSans-Bold" :font-size 9})
(def font-text {:color "rgba(128,128,128,1)" :font-family "OpenSans-Regular" :font-size 10})

(def font-button {:color prim :font-family "RobotoSlab-Bold" :font-size 17})
(def font-button-inverted {:color sec :font-family "RobotoSlab-Bold" :font-size 17})
(def font-heading-top-bar (merge font-heading1 {:font-size 20}))




;; ------------------ icons ---------------------
(def ReactNative (js/require "react-native"))
(def ReactNativeMaterialIcons (js/require "react-native-vector-icons/MaterialIcons"))

(defn assoc-margin [{:keys [w h anchor] :as style}]
  (assoc style
    :margin
    {:margin-left (- (* (:x anchor) w))
     :margin-top  (- (* (:y anchor) h))}))

(defn req-icon! [src-a style-a ic-name size color]
  (.then (.getImageSource ReactNativeMaterialIcons ic-name size color)
         (fn [src]
           (reset! src-a src)
           (rf/dispatch [:img-loaded])
           (.getSize (.-Image ReactNative) (.-uri src)
                     (fn [w h] (swap! style-a
                                      (fn [o]
                                        (rf/dispatch [:img-style-loaded])
                                        (-> (assoc o :w w :h h)
                                            (assoc-margin)))))))))

(def mat-ico (r/adapt-react-class (.-default ReactNativeMaterialIcons)))

(def no-of-imgs 30)

(def start-marker-color "#6666ff")
(def end-marker-color "#000099")
(def marker-color "#888888")

(def place-ic "place")
(def marker-ic "fiber-manual-record")
(def food-ic "restaurant")
(def cafe-ic "local-cafe")
(def viewpoint-ic "photo-camera")

(def marker-img-map (atom {}))

(defn get-image [name type]
  (reduce-kv (fn [agg k v] (assoc agg k (deref v))) {} (get-in @marker-img-map [name type])))

(defn create-marker-img [name normal-style-a map-style-a icon-name size size-map
                         color color-map]
  (let [create-img (fn [style-a size color]
                     (let [a       (atom nil)
                           a-start (atom nil)
                           a-end   (atom nil)]
                       (req-icon! a style-a icon-name size color)
                       (req-icon! a-start style-a icon-name size start-marker-color)
                       (req-icon! a-end style-a icon-name size end-marker-color)
                       {:img     a :img-start a-start
                        :img-end a-end :style style-a}))]
    (swap! marker-img-map
           (fn [o] (assoc o (keyword name) {:normal (create-img normal-style-a size color)
                                            :map    (create-img map-style-a size-map color-map)})))))

(defn init-styles! []
  (create-marker-img :place (atom {:anchor {:x 0.5 :y 1}}) (atom {:anchor {:x 0.5 :y 1}})
                     place-ic 10 40 marker-color marker-color)
  (create-marker-img :marker (atom {:anchor {:x 0.5 :y 0.5}}) (atom {:anchor {:x 0.5 :y 0.5}})
                     marker-ic 5 15 marker-color marker-color)
  (create-marker-img :food (atom {:anchor {:x 0.5 :y 0.5}}) (atom {:anchor {:x 0.5 :y 0.5}})
                     food-ic 8 20 marker-color marker-color)
  (create-marker-img :cafe (atom {:anchor {:x 0.5 :y 0.5}}) (atom {:anchor {:x 0.5 :y 0.5}})
                     cafe-ic 8 20 marker-color marker-color)
  (create-marker-img :viewpoint (atom {:anchor {:x 0.5 :y 0.5}}) (atom {:anchor {:x 0.5 :y 0.5}})
                     viewpoint-ic 8 20 marker-color marker-color))