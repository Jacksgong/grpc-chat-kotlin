package de.mkammerer.grpcchat.server

import de.mkammerer.grpcchat.protocol.*
import io.grpc.ServerBuilder
import io.grpc.stub.StreamObserver
import org.slf4j.LoggerFactory

fun main(args: Array<String>) {
    // the entrance.

    Server.start()
}

object Server {
    private const val PORT = 5001
    private val logger = LoggerFactory.getLogger(javaClass)

    fun start() {
        // the token generator
        val tokenGenerator = TokenGeneratorImpl
        // create service, all data is just stored in the memory.
        val userService = InMemoryUserService(tokenGenerator)
        val roomService = InMemoryRoomService


        val chat = Chat(userService, roomService)

        val server = ServerBuilder.forPort(PORT).addService(chat).build().start()
        Runtime.getRuntime().addShutdownHook(Thread({
            server.shutdown()
        }))
        logger.info("Server running on port {}", PORT)
        server.awaitTermination()
    }
}

/**
 * The real operation for the ChatGrpc.
 */
class Chat(
        // the user account service.
        private val userService: UserService,
        // the room service.
        private val roomService: RoomService

// just implement the operation of ChatGrpc which generate through protocol buffer and is handled by gRPC system.
) : ChatGrpc.ChatImplBase() {
    // the logger just used for debugging.
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * build an error response.
     */
    private fun buildError(code: Int, message: String): Error {
        return Error.newBuilder().setCode(code).setMessage(message).build()
    }

    override fun loginOrRegister(request: LoginRequest, responseObserver: StreamObserver<LoginOrRegisterResponse>) {
        val exist = userService.exists(request.username)

        val responseBuilder = LoginOrRegisterResponse.newBuilder()
        if (!exist) {
            logger.info("${request.username} isn't exist, so register for it first")
            val user = userService.register(request.username, request.password)
            logger.info("User {} registered", user.username)
            responseBuilder.performedRegister = true
        } else {
            responseBuilder.performedRegister = false
        }

        val token = userService.login(request.username, request.password)

        val response = if (token != null) {
            logger.info("User {} logged in. Access token is {}", request.username, token)
            responseBuilder.setLoggedIn(true).setToken(token.data).build()
        } else {
            responseBuilder.setLoggedIn(false).setError(buildError(LoginCodes.INVALID_CREDENTIALS, "Invalid credentials")).build()
        }

        // post the response.
        responseObserver.onNext(response)
        responseObserver.onCompleted()
    }

    override fun login(request: LoginRequest, responseObserver: StreamObserver<LoginResponse>) {
        val token = userService.login(request.username, request.password)
        val response = if (token != null) {
            // login successfully
            logger.info("User {} logged in. Access token is {}", request.username, token)
            LoginResponse.newBuilder().setLoggedIn(true).setToken(token.data).build()
        } else {
            LoginResponse.newBuilder().setLoggedIn(false).setError(buildError(LoginCodes.INVALID_CREDENTIALS, "Invalid credentials")).build()
        }

        // post the response.
        responseObserver.onNext(response)
        responseObserver.onCompleted()
    }

    override fun register(request: RegisterRequest, responseObserver: StreamObserver<RegisterResponse>) {
        val response = try {
            val user = userService.register(request.username, request.password)
            logger.info("User {} registered", user.username)

            // build the successfully register code.
            RegisterResponse.newBuilder().setRegistered(true).build()
        } catch (ex: UserAlreadyExistsException) {
            RegisterResponse.newBuilder().setRegistered(false).setError(buildError(RegisterCodes.USERNAME_ALREADY_EXISTS, "Username already exists")).build()
        }

        // post the response.
        responseObserver.onNext(response)
        responseObserver.onCompleted()
    }

    override fun createRoom(request: CreateRoomRequest, responseObserver: StreamObserver<CreateRoomResponse>) {
        // whether the user request create room is valid.
        val user = userService.validateToken(Token(request.token))

        val response = if (user == null) {
            logger.info("create room invalid token")
            // if user is invalid, create error response.
            CreateRoomResponse.newBuilder().setError(buildError(Codes.INVALID_TOKEN, "Invalid token")).setCreated(false).build()
        } else {
            try {
                // if user is valid, create room, and put the room to the room service.
                roomService.create(user, request.name, request.desc)
                // create the response for successfully creating the room.
                logger.info("create room successfully")
                CreateRoomResponse.newBuilder().setCreated(true).build()
            } catch(ex: RoomAlreadyExistsException) {
                // create the response for occurring error when creating the room.
                CreateRoomResponse.newBuilder().setCreated(false).setError(buildError(CreateRoomCodes.ROOM_ALREADY_EXISTS, "Room already exists")).build()
            }
        }

        // post the response.
        responseObserver.onNext(response)
        responseObserver.onCompleted()
    }

    override fun listRooms(request: ListRoomsRequest, responseObserver: StreamObserver<ListRoomsResponse>) {
        // whether the user request list all rooms is valid.
        val user = userService.validateToken(Token(request.token))

        val response = if (user == null) {
            logger.info("list rooms invalid token")
            ListRoomsResponse.newBuilder().setError(buildError(Codes.INVALID_TOKEN, "Invalid token")).build()
        } else {
            val rooms = roomService.all()
            logger.info("list rooms: ${rooms.size}")
            val roomMessageList = mutableListOf<RoomMessage>()
            rooms.forEach { roomMessageList.add(RoomMessage.newBuilder().setTitle(it.name).setDesc(it.desc).build()) }
            ListRoomsResponse.newBuilder().addAllRooms(roomMessageList).build()
        }

        // post the response.
        responseObserver.onNext(response)
        responseObserver.onCompleted()
    }
}