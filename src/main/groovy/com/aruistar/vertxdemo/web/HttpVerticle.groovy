package com.aruistar.vertxdemo.web

import groovy.util.logging.Slf4j
import io.vertx.core.AbstractVerticle
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.CookieHandler
import io.vertx.ext.web.handler.SessionHandler
import io.vertx.ext.web.handler.StaticHandler
import io.vertx.ext.web.handler.sockjs.SockJSHandler
import io.vertx.ext.web.sstore.LocalSessionStore

@Slf4j
class HttpVerticle extends AbstractVerticle {

    @Override
    void start() throws Exception {

        def httpConfig = config().getJsonObject("http", new JsonObject([port: 8899]))
        def eb = vertx.eventBus()

        Router router = Router.router(vertx)
        def port = httpConfig.getInteger("port", 8899)

        def opts = [
                inboundPermitteds : [
                        [
                                address: "chat.to.server"
                        ]
                ],
                outboundPermitteds: [
                        [
                                address: "chat.to.client"
                        ]
                ]
        ]

        // Create the event bus bridge and add it to the router.
        SockJSHandler ebHandler = SockJSHandler.create(vertx).bridge(opts);
        router.route("/messagebus/*").handler(ebHandler)

        def store = LocalSessionStore.create(vertx)
        def sessionHandler = SessionHandler.create(store)
        def cookieHandler = CookieHandler.create()
        router.route()
                .handler(cookieHandler)
                .handler(sessionHandler)

        router.get("/nickname")
                .handler({ routingContext ->

            def request = routingContext.request()
            def response = routingContext.response()

            def nickname = request.getParam("nickname")

            if (nickname && nickname.length() > 0) {
                routingContext.session().put("nickname", nickname)
            } else {
                nickname = routingContext.session().get("nickname")
            }

            if (nickname) {
                response.end(nickname)
                eb.publish("chat.to.client", [
                        from   : "⚙️",
                        context: "欢迎 @$nickname 加入聊天室，❤️❤️❤️❤️❤️❤️❤️❤️",
                        date   : new Date().format("yyyy-MM-dd HH:mm:ss:SSS")
                ])
            } else
                response.end()
        })

        router.route("/logout").handler({ routingContext ->

            def response = routingContext.response()
            routingContext.session().destroy()
            response.end()
        })

        // Create a router endpoint for the static content.
        router.route().handler(StaticHandler.create());

        eb.consumer("chat.to.server").handler({ message ->
            def body = message.body()
            body.date = new Date().format("yyyy-MM-dd HH:mm:ss:SSS")
            eb.publish("chat.to.client", body)
        })

        // Start the web server and tell it to use the router to handle requests.
        vertx.createHttpServer().requestHandler(router)
                .listen(port, { ar ->
            if (ar.succeeded()) {
                log.info("server is running on port " + port)
            } else {
                log.error("Could not start a HTTP server", ar.cause())
            }

        })
    }
}
