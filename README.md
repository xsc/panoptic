# panoptic

__panoptic__ monitors single files or whole directories for changes.

[![endorse](https://api.coderwall.com/xsc/endorsecount.png)](https://coderwall.com/xsc)

This is only for playing around for now. But much more functionality can be expected.

## Usage

__Leiningen__ ([via Clojars](https://clojars.org/panoptic))

```clojure
[panoptic "0.1.0-SNAPSHOT"]
```

__REPL__

```clojure
(use 'panoptic.core)

(def watcher
  (-> (simple-file-watcher ["/git/public/panoptic/test.txt"])
    (on-modify #(println (:path %) "changed"))
    (on-create #(println (:path %) "created"))
    (on-delete #(println (:path %) "deleted"))
    (start-watcher!)))

...

@(watcher)
```

## License

Copyright &copy; 2013 Yannick Scherer

Distributed under the Eclipse Public License, the same as Clojure.
