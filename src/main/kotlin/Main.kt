import com.google.gson.Gson
import com.google.gson.JsonObject
import com.sun.net.httpserver.HttpServer
import io.github.kookybot.annotation.Filter
import io.github.kookybot.client.Client
import io.github.kookybot.commands.CommandSource
import io.github.kookybot.contract.TextChannel
import io.github.kookybot.events.EventHandler
import io.github.kookybot.events.Listener
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress

class MyListener: Listener {
    var repo2channel = mutableMapOf<String, MutableList<String>>()
    init {
        val file = File("data/r2c.json")
        if (!file.exists()) {
            file.writeText("{}")
        }
        repo2channel = Gson().fromJson(file.readText(), repo2channel::class.java)
    }
    fun save() {
        File("data/r2c.json").writeText(Gson().toJson(repo2channel))
    }
    @EventHandler
    @Filter("git.alive")
    fun alive(source: CommandSource) {
        source.sendMessage("alive")
    }
    @EventHandler
    @Filter("git.bind({repo,\\w+/\\w+})")
    fun bind(repo: String, source: CommandSource) {
        if (source.type != CommandSource.Type.Channel) return
        repo2channel[repo] = repo2channel[repo] ?: mutableListOf()
        repo2channel[repo]!!.add(source.channel!!.id)
        save()
        source.sendMessage("bind $repo to this channel!")
    }
}

suspend fun main(args: Array<String>) {
    val client = Client("1/MTIxNjE=/jo1GnVpFTJ709ulCgCJxxQ==")
    val listener = MyListener()
    client.eventManager.addClassListener(listener)
    client.start()
    val server = HttpServer.create(InetSocketAddress(InetAddress.getLocalHost(), 30550), 0)
    server.start()
    server.createContext("/github") {
        println(it.requestURI.toString())
        var response = ""
        if (it.requestMethod == "POST") {
            val json = Gson().fromJson(it.requestBody.readAllBytes().decodeToString(), JsonObject::class.java)
            println(json)
            when (it.requestHeaders["X"]?.firstOrNull() ?: "") {
                "push" -> {
                    val repo = json["repository"].asJsonObject["full_name"].asString
                    val repoUrl = json["repository"].asJsonObject["url"].asString
                    val sender = json["login"].asString
                    val senderUrl = json["url"].asString
                    val before = json["before"].asString.substring(0 until 6)
                    val after = json["after"].asString.substring(0 until 6)
                    val commits = json["commits"].asJsonArray.map {
                        val c = it.asJsonObject
                        object {
                            val id = c["id"].asString.substring(0 until 6)
                        }
                    }

                    listener.repo2channel[repo]?.map { client.self!!.getChannel(it) }?.filterIsInstance<TextChannel>()?.forEach {
                        it.sendCardMessage {
                            Card {
                                HeaderModule(
                                    PlainTextElement("New ppush event to $repo")
                                )
                                Divider()
                                ContextModule {
                                    MarkdownElement("[$sender]($senderUrl) pushed ")
                                }
                            }
                        }
                    }
                }
            }
        } else if (it.requestMethod == "GET") {
            response += """
                Steps to use KookyGithub:
                1. Set webhook url as <http://slv4.starlight.cool:30550>
                2. TODO!!!
                
                Donate/Contract me: Steve47876#0001
                
            """.trimIndent()
        }
        response += "\nKookyGithub(c) 2022, zly2006"
        it.sendResponseHeaders(200, 0)
        it.responseBody.write(response.toByteArray())
        it.responseBody.close()
    }
    println("Hello World!")
}