import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.stream.Stream;

public class InetTest {

    public static void main(String[] args) throws UnknownHostException, SocketException {
        Enumeration<NetworkInterface> enumeration = NetworkInterface.getNetworkInterfaces();
        while(enumeration.hasMoreElements()){
            NetworkInterface networkInterface = enumeration.nextElement();
            Enumeration<InetAddress> inetAddressEnumeration = networkInterface.getInetAddresses();
            System.out.println(networkInterface);
            while(inetAddressEnumeration.hasMoreElements()){
                InetAddress inetAddress = inetAddressEnumeration.nextElement();
                System.out.println(inetAddress);
            }
        }
    }
}
