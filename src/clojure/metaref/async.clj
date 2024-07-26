(ns metaref.async
  (:import [java.util.concurrent ThreadFactory]
           [metaref.async
            CSPChannel
            Channel
            ChannelOps
            ChannelOps$AltResult]))

(set! *warn-on-reflection* true)

(defn chan
  "Creates a new channel with the given capacity. If no capacity is given,
   the channel will be unbuffered."
  (^Channel []
   (CSPChannel.))
  (^Channel [^long n]
   {:pre [(< 0 n)]}
   (CSPChannel. n)))

(defn channel?
  "Returns true if the given value is an metaref.async/Channel type, false otherwise."
  [ch]
  (instance? Channel ch))

(defn >!
  "Puts the given value on the channel. If the channel is full, blocks until
   there is space available.
   
   If the put is successful, returns true, otherwise false

   Throws an exception if the value is nil."
  [ch v]
  (when (nil? v)
    (throw (ex-info "Can't put nil on a channel" {})))
  (.put ^Channel ch v))

(defn <!
  "Takes a value from the channel. If the channel is empty, blocks until a
     value is available.
    
     If the channel is closed and empty, returns nil."
  [ch]
  (.take ^Channel ch))

(defn close!
  "Closes the channel. Any pending puts will return false. Any pending takes
   will return a value if available, or nil if the channel is empty."
  [ch]
  (.close ^Channel ch))

(defn closed?
  "Returns true if the channel is closed, false otherwise."
  [ch]
  (.isClosed ^Channel ch))

(defn capacity
  "Returns the capacity of the channel."
  [ch]
  (.capacity ^Channel ch))

(defonce ^:private ^ThreadFactory vthread-factory
  (.. Thread
      (ofVirtual)
      (name "metaref-async-worker-" 0)
      (factory)))

(defn vthread-call
  "Executes the given *function* in a new thread, returning a channel which will
   contain the result of the function when it completes.

   See metaref.async/go macro for a more convenient way to call this function
   
   If the function throws an exception, the channel will contain an ex-info
   with a data map with keys :exception, :thread and :thread-name.
   
   If the function returns nil, the channel will contain the keyword :metaref.async/nil.
   
   The thread will be named 'metaref-async-worker-<n>', where <n> is a number
   "
  [f]
  (let [ch     (chan)
        exec-f (bound-fn []
                 (try
                   (let [result (f)]
                     (when-not (nil? result)
                       (>! ch result)))
                   (catch Throwable t
                     (let [thread      (Thread/currentThread)
                           thread-name (.getName thread)]
                       (>! ch (ex-info (str "Error in go block: " t)
                                       {:exception t
                                        :thread thread
                                        :thread-name thread-name}))))
                   (finally
                     (close! ch))))]
    (.start
     (.newThread vthread-factory
                 exec-f))
    ch))

(defmacro go
  "
   Executes the the expression in a new thread, returning a channel which will
   contain the result of the body when it completes.
   
   If the body throws an exception, the channel will contain an ex-info
   with a data map with keys :exception, :thread and :thread-name.
   
   If the body returns nil, the channel will contain the keyword :metaref.async/nil.

   The thread will be named 'metaref-async-worker-<n>', where <n> is a number
   "
  [& body]
  `(vthread-call (fn [] ~@body)))

(defn timeout
  "Returns a channel which will close after the given number of milliseconds."
  [ms]
  (let [ch (chan)]
    (go
      (Thread/sleep (long ms))
      (close! ch))
    ch))

(defmacro go-loop
  "Like (go (loop ...))"
  [bindings & body]
  `(go (loop ~bindings ~@body)))

(defn select!
  "Takes a vector of either a channel or a vector of [channel value]
   and returns a vector containing the value taken and the channel it was taken from for takes
   and [true channel] for puts. 
   If no channel is ready, blocks until one
   is. If more than one channel is ready, selects one at random.
   
   If priority is true, the first channel in the vector will be selected first
   instead of a random channel.

   If default is supplied, it will be returned if no channel is ready 
   [default :default] is returned in this case"
  [ops & {:keys [priority default] :or {priority false default nil}}]
  (let [^ChannelOps$AltResult result (ChannelOps/alt ops priority (boolean default))
        value (.value result)
        port  (.channel result)]
    (if (and (nil? value) (nil? port))
      [default :default]
      [value port])))

(defn pipe
  "Takes elements from the from channel and supplies them to the to
  channel. By default, the to channel will be closed when the from
  channel closes, but can be determined by the close?  parameter. Will
  stop consuming the from channel if the to channel closes"
  ([from to] (pipe from to true))
  ([from to close?]
   (go-loop []
     (let [v (<! from)]
       (if (nil? v)
         (when close?
           (close! to))
         (when (>! to v)
           (recur)))))
   to))

(defmacro for-each
  "bindings => name channel

  Repeatedly executes body until channel is closed, binding name to
  each value taken from the channel"
  [bindings & body]
  (#'clojure.core/assert-args
   (vector? bindings) "a vector for its binding"
   (= 2 (count bindings)) "exactly 2 forms in binding vector")
  (let [val (first bindings)
        n   (second bindings)]
    `(loop [~val (<! ~n)]
       (when ~val
         ~@body
         (recur (<! ~n))))))
