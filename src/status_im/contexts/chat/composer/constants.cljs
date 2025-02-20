(ns status-im.contexts.chat.composer.constants
  (:require
    [quo.foundations.typography :as typography]
    [react-native.platform :as platform]))

(def ^:const bar-container-height 20)

(def ^:const input-height 32)

(def ^:const actions-container-height 56)

(def ^:const composer-default-height (+ bar-container-height input-height actions-container-height))

(def ^:const line-height (if platform/ios? 18 (:line-height typography/paragraph-1)))

(def ^:const multiline-minimized-height (+ input-height line-height))

(def ^:const empty-opacity 0.7)

(def ^:const images-padding-top 12)
(def ^:const images-padding-bottom 8)
(def ^:const images-container-height
  (+ actions-container-height images-padding-top images-padding-bottom))

(def ^:const links-padding-top 12)
(def ^:const links-padding-bottom 8)
(def ^:const links-container-height
  (+ actions-container-height links-padding-top links-padding-bottom))

(def ^:const reply-container-height 32)

(def ^:const edit-container-height 32)

(def ^:const mentions-max-height 240)

(def ^:const extra-content-offset (if platform/ios? 6 -8))

(def ^:const content-change-threshold 10)

(def ^:const drag-threshold 30)

(def ^:const velocity-threshold (if platform/ios? -1000 -500))

(def ^:const background-threshold 0.75)

(def ^:const max-text-size 4096)

(def ^:const unfurl-debounce-ms
  "Use a high threshold to prevent unnecessary rendering overhead."
  400)
