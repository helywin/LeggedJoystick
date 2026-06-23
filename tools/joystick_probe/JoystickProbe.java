import java.io.FileInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class JoystickProbe {
    private static final int CMD_CHANNELS = 0x42;
    private static final int[] CRC16_TABLE = {
            0x0, 0x1021, 0x2042, 0x3063, 0x4084, 0x50a5, 0x60c6, 0x70e7,
            0x8108, 0x9129, 0xa14a, 0xb16b, 0xc18c, 0xd1ad, 0xe1ce, 0xf1ef,
            0x1231, 0x210, 0x3273, 0x2252, 0x52b5, 0x4294, 0x72f7, 0x62d6,
            0x9339, 0x8318, 0xb37b, 0xa35a, 0xd3bd, 0xc39c, 0xf3ff, 0xe3de,
            0x2462, 0x3443, 0x420, 0x1401, 0x64e6, 0x74c7, 0x44a4, 0x5485,
            0xa56a, 0xb54b, 0x8528, 0x9509, 0xe5ee, 0xf5cf, 0xc5ac, 0xd58d,
            0x3653, 0x2672, 0x1611, 0x630, 0x76d7, 0x66f6, 0x5695, 0x46b4,
            0xb75b, 0xa77a, 0x9719, 0x8738, 0xf7df, 0xe7fe, 0xd79d, 0xc7bc,
            0x48c4, 0x58e5, 0x6886, 0x78a7, 0x840, 0x1861, 0x2802, 0x3823,
            0xc9cc, 0xd9ed, 0xe98e, 0xf9af, 0x8948, 0x9969, 0xa90a, 0xb92b,
            0x5af5, 0x4ad4, 0x7ab7, 0x6a96, 0x1a71, 0xa50, 0x3a33, 0x2a12,
            0xdbfd, 0xcbdc, 0xfbbf, 0xeb9e, 0x9b79, 0x8b58, 0xbb3b, 0xab1a,
            0x6ca6, 0x7c87, 0x4ce4, 0x5cc5, 0x2c22, 0x3c03, 0xc60, 0x1c41,
            0xedae, 0xfd8f, 0xcdec, 0xddcd, 0xad2a, 0xbd0b, 0x8d68, 0x9d49,
            0x7e97, 0x6eb6, 0x5ed5, 0x4ef4, 0x3e13, 0x2e32, 0x1e51, 0xe70,
            0xff9f, 0xefbe, 0xdfdd, 0xcffc, 0xbf1b, 0xaf3a, 0x9f59, 0x8f78,
            0x9188, 0x81a9, 0xb1ca, 0xa1eb, 0xd10c, 0xc12d, 0xf14e, 0xe16f,
            0x1080, 0xa1, 0x30c2, 0x20e3, 0x5004, 0x4025, 0x7046, 0x6067,
            0x83b9, 0x9398, 0xa3fb, 0xb3da, 0xc33d, 0xd31c, 0xe37f, 0xf35e,
            0x2b1, 0x1290, 0x22f3, 0x32d2, 0x4235, 0x5214, 0x6277, 0x7256,
            0xb5ea, 0xa5cb, 0x95a8, 0x8589, 0xf56e, 0xe54f, 0xd52c, 0xc50d,
            0x34e2, 0x24c3, 0x14a0, 0x481, 0x7466, 0x6447, 0x5424, 0x4405,
            0xa7db, 0xb7fa, 0x8799, 0x97b8, 0xe75f, 0xf77e, 0xc71d, 0xd73c,
            0x26d3, 0x36f2, 0x691, 0x16b0, 0x6657, 0x7676, 0x4615, 0x5634,
            0xd94c, 0xc96d, 0xf90e, 0xe92f, 0x99c8, 0x89e9, 0xb98a, 0xa9ab,
            0x5844, 0x4865, 0x7806, 0x6827, 0x18c0, 0x8e1, 0x3882, 0x28a3,
            0xcb7d, 0xdb5c, 0xeb3f, 0xfb1e, 0x8bf9, 0x9bd8, 0xabbb, 0xbb9a,
            0x4a75, 0x5a54, 0x6a37, 0x7a16, 0xaf1, 0x1ad0, 0x2ab3, 0x3a92,
            0xfd2e, 0xed0f, 0xdd6c, 0xcd4d, 0xbdaa, 0xad8b, 0x9de8, 0x8dc9,
            0x7c26, 0x6c07, 0x5c64, 0x4c45, 0x3ca2, 0x2c83, 0x1ce0, 0xcc1,
            0xef1f, 0xff3e, 0xcf5d, 0xdf7c, 0xaf9b, 0xbfba, 0x8fd9, 0x9ff8,
            0x6e17, 0x7e36, 0x4e55, 0x5e74, 0x2e93, 0x3eb2, 0xed1, 0x1ef0
    };

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            usage();
            return;
        }
        Map<String, String> options = parseOptions(Arrays.copyOfRange(args, 1, args.length));
        switch (args[0]) {
            case "local-reuse":
                runLocalReuse(options);
                break;
            case "recv":
                runReceiver(options);
                break;
            case "send":
                runSender(options);
                break;
            case "unirc-udp":
                runUnircUdp(options);
                break;
            case "serial-read":
                runSerialRead(options);
                break;
            default:
                usage();
        }
    }

    private static void runLocalReuse(Map<String, String> options) throws Exception {
        int port = intOption(options, "port", 41986);
        int count = intOption(options, "count", 20);
        AtomicInteger first = new AtomicInteger();
        AtomicInteger second = new AtomicInteger();
        CountDownLatch ready = new CountDownLatch(2);
        Thread firstThread = receiverThread("receiver-1", port, true, count, first, ready);
        Thread secondThread = receiverThread("receiver-2", port, true, count, second, ready);
        firstThread.start();
        secondThread.start();
        ready.await(2, TimeUnit.SECONDS);
        runSender(Map.of("host", "127.0.0.1", "port", String.valueOf(port), "count", String.valueOf(count)));
        firstThread.join();
        secondThread.join();
        log("local-reuse result: receiver-1=" + first.get() + ", receiver-2=" + second.get());
    }

    private static Thread receiverThread(
            String name,
            int port,
            boolean reuse,
            int expected,
            AtomicInteger received,
            CountDownLatch ready
    ) {
        return new Thread(() -> {
            try (DatagramSocket socket = openSocket(port, reuse)) {
                socket.setSoTimeout(1500);
                ready.countDown();
                byte[] buffer = new byte[2048];
                long deadline = System.currentTimeMillis() + 5000L;
                while (received.get() < expected && System.currentTimeMillis() < deadline) {
                    try {
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                        socket.receive(packet);
                        received.incrementAndGet();
                        log(name + " received " + packet.getLength() + " bytes from " + packet.getSocketAddress());
                    } catch (SocketTimeoutException ignored) {
                        break;
                    }
                }
            } catch (IOException e) {
                log(name + " failed: " + e.getMessage());
            }
        }, name);
    }

    private static void runReceiver(Map<String, String> options) throws Exception {
        int port = intOption(options, "port", 41986);
        int seconds = intOption(options, "seconds", 20);
        boolean reuse = booleanOption(options, "reuse", true);
        try (DatagramSocket socket = openSocket(port, reuse)) {
            socket.setSoTimeout(1000);
            log("recv bound local=" + socket.getLocalSocketAddress() + ", reuse=" + reuse);
            receiveLoop(socket, System.currentTimeMillis() + seconds * 1000L, true);
        }
    }

    private static void runSender(Map<String, String> options) throws Exception {
        String host = stringOption(options, "host", "127.0.0.1");
        int port = intOption(options, "port", 41986);
        int count = intOption(options, "count", 20);
        InetAddress address = InetAddress.getByName(host);
        try (DatagramSocket socket = new DatagramSocket()) {
            for (int index = 0; index < count; index++) {
                byte[] payload = ("probe-" + index).getBytes();
                socket.send(new DatagramPacket(payload, payload.length, address, port));
                log("sent probe-" + index + " to " + host + ":" + port);
                Thread.sleep(50);
            }
        }
    }

    private static void runUnircUdp(Map<String, String> options) throws Exception {
        String remote = stringOption(options, "remote", "192.168.144.20");
        int remotePort = intOption(options, "remote-port", 19856);
        int localPort = intOption(options, "local-port", 41986);
        int freq = intOption(options, "freq", 5);
        int seconds = intOption(options, "seconds", 15);
        boolean reuse = booleanOption(options, "reuse", true);
        InetAddress remoteAddress = InetAddress.getByName(remote);
        try (DatagramSocket socket = openSocket(localPort, reuse)) {
            socket.setSoTimeout(1000);
            log("unirc-udp local=" + socket.getLocalSocketAddress() + ", remote=" + remote + ":" + remotePort + ", freq=" + freq);
            byte[] enable = buildChannelRequest(freq, 0);
            for (int index = 0; index < 3; index++) {
                socket.send(new DatagramPacket(enable, enable.length, remoteAddress, remotePort));
                log("sent enable frame " + (index + 1) + "/3: " + toHex(enable, enable.length));
                Thread.sleep(100);
            }
            receiveLoop(socket, System.currentTimeMillis() + seconds * 1000L, true);
        }
    }

    private static void runSerialRead(Map<String, String> options) throws Exception {
        String device = stringOption(options, "device", "/dev/ttyHS3");
        int seconds = intOption(options, "seconds", 15);
        long deadline = System.currentTimeMillis() + seconds * 1000L;
        log("serial-read device=" + device + ", seconds=" + seconds);
        try (FileInputStream input = new FileInputStream(device)) {
            FrameParser parser = new FrameParser();
            byte[] buffer = new byte[512];
            while (System.currentTimeMillis() < deadline) {
                int read = input.read(buffer);
                if (read < 0) {
                    break;
                }
                for (int index = 0; index < read; index++) {
                    byte[] frame = parser.push(buffer[index]);
                    if (frame != null) {
                        logFrame("serial", frame, frame.length);
                    }
                }
            }
        }
    }

    private static DatagramSocket openSocket(int port, boolean reuse) throws IOException {
        DatagramSocket socket = new DatagramSocket(null);
        socket.setReuseAddress(reuse);
        socket.bind(new InetSocketAddress(port));
        return socket;
    }

    private static void receiveLoop(DatagramSocket socket, long deadline, boolean decode) throws IOException {
        int count = 0;
        byte[] buffer = new byte[4096];
        while (System.currentTimeMillis() < deadline) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                count++;
                log("received #" + count + " " + packet.getLength() + " bytes from " + packet.getSocketAddress());
                if (decode) {
                    logFrame("udp", packet.getData(), packet.getLength());
                }
            } catch (SocketTimeoutException ignored) {
                log("receive timeout");
            }
        }
        log("received total=" + count);
    }

    private static byte[] buildChannelRequest(int freq, int seq) {
        byte[] frame = new byte[11];
        frame[0] = 0x55;
        frame[1] = 0x66;
        frame[2] = 0x01;
        frame[3] = 0x01;
        frame[4] = 0x00;
        frame[5] = (byte) (seq & 0xff);
        frame[6] = (byte) ((seq >> 8) & 0xff);
        frame[7] = CMD_CHANNELS;
        frame[8] = (byte) (freq & 0xff);
        int crc = crc16(frame, 0, frame.length - 2);
        frame[9] = (byte) (crc & 0xff);
        frame[10] = (byte) ((crc >> 8) & 0xff);
        return frame;
    }

    private static void logFrame(String source, byte[] data, int length) {
        if (length < 10 || data[0] != 0x55 || data[1] != 0x66) {
            log(source + " raw=" + toHex(data, Math.min(length, 64)));
            return;
        }
        int dataLength = u8(data[3]) | (u8(data[4]) << 8);
        int expectedLength = 8 + dataLength + 2;
        if (length < expectedLength) {
            log(source + " partial frame len=" + length + ", expected=" + expectedLength + ", raw=" + toHex(data, length));
            return;
        }
        int cmd = u8(data[7]);
        int packetCrc = u8(data[8 + dataLength]) | (u8(data[9 + dataLength]) << 8);
        int computedCrc = crc16(data, 0, 8 + dataLength);
        log(source + " frame ctrl=0x" + hex(u8(data[2]), 2)
                + " seq=" + (u8(data[5]) | (u8(data[6]) << 8))
                + " cmd=0x" + hex(cmd, 2)
                + " dataLen=" + dataLength
                + " crc=" + (packetCrc == computedCrc ? "ok" : "bad expected=0x" + hex(computedCrc, 4)));
        if (cmd == CMD_CHANNELS && dataLength >= 32) {
            ByteBuffer buffer = ByteBuffer.wrap(data, 8, 32).order(ByteOrder.LITTLE_ENDIAN);
            int[] channels = new int[16];
            for (int index = 0; index < channels.length; index++) {
                channels[index] = buffer.getShort();
            }
            log(source + " channels=" + Arrays.toString(channels));
        }
    }

    private static int crc16(byte[] data, int offset, int length) {
        int crc = 0;
        for (int index = offset; index < offset + length; index++) {
            int temp = (crc >> 8) & 0xff;
            crc = ((crc << 8) ^ CRC16_TABLE[(data[index] & 0xff) ^ temp]) & 0xffff;
        }
        return crc & 0xffff;
    }

    private static String toHex(byte[] data, int length) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < length; index++) {
            if (index > 0) {
                builder.append(' ');
            }
            builder.append(hex(data[index] & 0xff, 2));
        }
        return builder.toString();
    }

    private static String hex(int value, int width) {
        String result = Integer.toHexString(value).toUpperCase();
        while (result.length() < width) {
            result = "0" + result;
        }
        return result;
    }

    private static int u8(byte value) {
        return value & 0xff;
    }

    private static Map<String, String> parseOptions(String[] args) {
        Map<String, String> options = new HashMap<>();
        for (int index = 0; index < args.length; index++) {
            if (!args[index].startsWith("--")) {
                continue;
            }
            String key = args[index].substring(2);
            String value = index + 1 < args.length && !args[index + 1].startsWith("--")
                    ? args[++index]
                    : "true";
            options.put(key, value);
        }
        return options;
    }

    private static int intOption(Map<String, String> options, String key, int fallback) {
        return options.containsKey(key) ? Integer.parseInt(options.get(key)) : fallback;
    }

    private static boolean booleanOption(Map<String, String> options, String key, boolean fallback) {
        return options.containsKey(key) ? Boolean.parseBoolean(options.get(key)) : fallback;
    }

    private static String stringOption(Map<String, String> options, String key, String fallback) {
        return options.getOrDefault(key, fallback);
    }

    private static void log(String message) {
        System.out.println(Instant.now() + " " + message);
    }

    private static void usage() {
        System.out.println("Usage:");
        System.out.println("  JoystickProbe local-reuse --port 41986 --count 20");
        System.out.println("  JoystickProbe recv --port 41986 --seconds 20 --reuse true");
        System.out.println("  JoystickProbe send --host 127.0.0.1 --port 41986 --count 20");
        System.out.println("  JoystickProbe unirc-udp --remote 192.168.144.20 --remote-port 19856 --local-port 41986 --freq 5 --seconds 15");
        System.out.println("  JoystickProbe serial-read --device /dev/ttyHS3 --seconds 15");
    }

    private static final class FrameParser {
        private final byte[] buffer = new byte[4096];
        private int size = 0;
        private int expected = -1;

        byte[] push(byte value) {
            if (size == 0 && value != 0x55) {
                return null;
            }
            if (size == 1 && value != 0x66) {
                size = value == 0x55 ? 1 : 0;
                return null;
            }
            buffer[size++] = value;
            if (size == 5) {
                int dataLength = u8(buffer[3]) | (u8(buffer[4]) << 8);
                expected = 8 + dataLength + 2;
                if (expected > buffer.length) {
                    size = 0;
                    expected = -1;
                    return null;
                }
            }
            if (expected > 0 && size == expected) {
                byte[] frame = Arrays.copyOf(buffer, expected);
                size = 0;
                expected = -1;
                return frame;
            }
            return null;
        }
    }
}
