import com.google.gson.Gson
import com.google.gson.JsonObject
import com.sun.net.httpserver.HttpServer
import io.github.kookybot.annotation.Filter
import io.github.kookybot.client.Client
import io.github.kookybot.commands.CommandSource
import io.github.kookybot.contract.TextChannel
import io.github.kookybot.events.EventHandler
import io.github.kookybot.events.Listener
import io.github.kookybot.events.channel.ChannelMessageEvent
import io.github.kookybot.message.CardMessage
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
    fun c(channelMessageEvent: ChannelMessageEvent) {
       // println(channelMessageEvent.content)
    }
    @EventHandler
    @Filter("git.bind\\({repo,[\\w\\-]+/[\\w\\-]+}\\)")
    fun bind(repo: String, source: CommandSource) {
        Regex("[\\w\\-]+/[\\w\\-]+")
        if (source.type != CommandSource.Type.Channel) return
        repo2channel[repo] = repo2channel[repo] ?: mutableListOf()
        if (!repo2channel[repo]!!.contains(source.channel!!.id)) {
            repo2channel[repo]!!.add(source.channel!!.id)
            save()
        }
        source.sendMessage("bind $repo to this channel!")
    }
}

suspend fun main(args: Array<String>) {

    val client = Client("1/MTIxNjE=/jo1GnVpFTJ709ulCgCJxxQ==") {
        enableCommand = true
        responseCommandExceptions = false
        enablePermission = true
        botMarketUUID = "8ba95008-498e-4e17-927e-af208547ec5f"
    }
    val listener = MyListener()
    fun sendCard(repo: String, content: CardMessage.MessageScope.() -> Unit) {
        listener.repo2channel[repo]?.map { cid -> client.self!!.getChannel(cid) }
            ?.filterIsInstance<TextChannel>()
            ?.forEach { it.sendCardMessage(content = content) }
    }
    client.eventManager.addListener<ChannelMessageEvent> {
        println(content)
    }
    client.eventManager.addClassListener(listener)
    client.start()
    val server = HttpServer.create(InetSocketAddress(InetAddress.getByAddress(byteArrayOf(0, 0, 0, 0)), 30550), 0)
    server.start()
    server.createContext("/github") {
        var response = ""
        if (it.requestMethod == "POST") {
            val json = Gson().fromJson(it.requestBody.readAllBytes().decodeToString(), JsonObject::class.java)
            println(json)
            when (it.requestHeaders["X-GitHub-Event"]?.firstOrNull() ?: "") {
                "pull_request" -> {
                    val action = json["action"].asString
                    val number = json["number"].asInt
                    val repo = json["repository"].asJsonObject["full_name"].asString
                    val repoUrl = json["repository"].asJsonObject["url"].asString
                    val sender = json["sender"].asJsonObject["login"].asString
                    val senderUrl = json["sender"].asJsonObject["url"].asString
                    val url = json["pull_request"].asJsonObject["html_url"].asString
                    val title = json["pull_request"].asJsonObject["title"].asString
                    val status = json["pull_request"].asJsonObject["state"].asString
                    when (action) {
                        "closed" -> {
                            sendCard(repo) {
                                Card {
                                    HeaderModule(
                                        PlainTextElement("New PR at $repo")
                                    )
                                    Divider()
                                    SectionModule(
                                        text = MarkdownElement(
                                            "[$sender]($senderUrl) closed PR [#$number]($url) at [$repo]($repoUrl)"
                                        ),
                                        accessory = ImageElement(
                                            json["sender"].asJsonObject["avatar_url"].asString,
                                            "face",
                                            CardMessage.Size.SM,
                                            true
                                        ),
                                        mode = CardMessage.LeftRight.Left,
                                    )
                                    SectionModule(
                                        text = MarkdownElement(title)
                                    )
                                    ContextModule {
                                        PlainTextElement("Status: $status")
                                    }
                                }
                            }
                        }
                        "opened" -> {
                            sendCard(repo) {
                                Card {
                                    HeaderModule(
                                        PlainTextElement("New PR at $repo")
                                    )
                                    Divider()
                                    SectionModule(
                                        text = MarkdownElement(
                                            "[$sender]($senderUrl) opened PR [#$number]($url) at [$repo]($repoUrl)"
                                        ),
                                        accessory = ImageElement(
                                            json["sender"].asJsonObject["avatar_url"].asString,
                                            "face",
                                            CardMessage.Size.SM,
                                            true
                                        ),
                                        mode = CardMessage.LeftRight.Left,
                                    )
                                    SectionModule(
                                        text = MarkdownElement(title)
                                    )
                                    json["pull_request"].asJsonObject["body"].let {
                                        if (!it.isJsonNull) {
                                            ContextModule {
                                                MarkdownElement(it.asString)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                "push" -> {
                    val ref = json["ref"].asString
                    val repo = json["repository"].asJsonObject["full_name"].asString
                    val repoUrl = json["repository"].asJsonObject["url"].asString
                    val sender = json["sender"].asJsonObject["login"].asString
                    val senderUrl = json["sender"].asJsonObject["url"].asString
                    val before = json["before"].asString.substring(0 until 8)
                    val after = json["after"].asString.substring(0 until 8)
                    println("$repo $ref")
                    if (ref.startsWith("efs/tags/")) return@createContext
                    val branch = ref.substring(11)
                    val commits = json["commits"].asJsonArray.map {
                        val c = it.asJsonObject
                        object {
                            val id = c["id"].asString.substring(0 until 8)
                            val url = c["url"].asString
                            val message = c["message"].asString
                            val author = c["author"].asJsonObject["name"].asString
                            override fun toString(): String =
                                "[$id]($url) by $author: $message"
                        }
                    }

                    sendCard(repo) {
                        Card {
                            HeaderModule(
                                PlainTextElement("New push event to $repo:$branch")
                            )
                            Divider()
                            SectionModule(
                                text = MarkdownElement("[$sender]($senderUrl) pushed ${commits.size} commit to [$repo]($repoUrl)"),
                                accessory = ImageElement(
                                    json["sender"].asJsonObject["avatar_url"].asString,
                                    "face",
                                    CardMessage.Size.SM,
                                    true
                                ),
                                mode = CardMessage.LeftRight.Left,
                            )
                            ContextModule {
                                MarkdownElement("$before -> $after")
                            }
                            commits.forEach {
                                SectionModule(
                                    text = MarkdownElement(it.toString())
                                )
                            }
                        }
                    }
                }
            }
        } else if (it.requestMethod == "GET") {
            response += """
                Steps to use KookyGithub:
                1. Set webhook url to <http://slv4.starlight.cool:30550>
                2. Set content-type to application/json.
                3. Use git.bind(<user>/<repo>) to make current channel receive event messages.
                
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
