;; This is an example configuration
:example/server
{:port  ~port
 :url   "http://~{host}:~{port}/example"
 ~@server-options}

:example/stream
{:filter #re #"\d+"
 :url    ~(ref :example/server :url)}

:example/text
{:environment ~(? prod "production" dev "development")
 :description """
Some long description
Multiple lines
With "Inner quotations"
And maybe a variable like ~{port}.
"""}
