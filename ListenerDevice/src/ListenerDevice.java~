
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;


public class ListenerDevice
{
    public static final int PORT_NO = 5432;
    public static final String GROUP_ADDR = "225.4.5.6";
    public static final int DGRAM_LEN = 1024;
    public static final String WHO_IS = "Who is?";
    public static final int TIME_TO_LIVE = 1;
    public static void main(String[] args)
    {
        MulticastSocket socket = null;
        DatagramPacket inPacket = null;
        byte[] inBuf = new byte[DGRAM_LEN];
        try
        {
          //Prepare to join multicast group
          socket = new MulticastSocket(PORT_NO);
          InetAddress address = InetAddress.getByName(GROUP_ADDR);
          socket.joinGroup(address);

              while(true)
              {
                    System.out.println("Listening..." + '\n');
                    inPacket = new DatagramPacket(inBuf, inBuf.length);
                    socket.receive(inPacket);
                    String msg = new String(inBuf, 0, inPacket.getLength());
                    System.out.println("From: " + inPacket.getAddress() + " --- Msg: " + "My Url is " + msg + '\n');
              }
        }
        catch(Exception ioe)
        {
            System.out.println(ioe);
        }
      }
}
