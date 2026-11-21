package github.salemby.tododevops.support;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** A local TCP relay in front of a service; closing it cuts every established connection at once. */
public final class TcpRelay implements AutoCloseable {
    private final ServerSocket server;
    private final List<Socket> sockets = new CopyOnWriteArrayList<>();

    public TcpRelay(int targetPort) throws IOException {
        server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        daemon(() -> {
            while (!server.isClosed()) {
                try {
                    Socket client = server.accept();
                    Socket target = new Socket(InetAddress.getLoopbackAddress(), targetPort);
                    sockets.add(client);
                    sockets.add(target);
                    pipe(client, target);
                    pipe(target, client);
                } catch (IOException closed) {
                    return;
                }
            }
        });
    }

    private static void daemon(Runnable task) {
        var thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

    public int port() {
        return server.getLocalPort();
    }

    private static void pipe(Socket from, Socket to) {
        daemon(() -> {
            try (InputStream in = from.getInputStream(); OutputStream out = to.getOutputStream()) {
                in.transferTo(out);
            } catch (IOException closed) {
                // the relay was closed or a side hung up
            }
        });
    }

    @Override
    public void close() throws IOException {
        server.close();
        for (Socket socket : sockets) socket.close();
    }
}
