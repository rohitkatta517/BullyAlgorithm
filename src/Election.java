import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.Timer;
import java.net.SocketTimeoutException;

import javax.swing.*;

/**
 * @author Rohit Katta
 * @UID: 1001512896
 * @NetID: rxk2896
 * <p>
 * Ring Coordnator algorithm
 * Each process starts seperately, when last process starts election call is made by last.
 * UI has 3 buttons. Election button to manually start election.
 * Stop button to stop the process.
 * Restart button to put process again in ring.
 */
public class Election extends JFrame {
    JFrame frame = new JFrame("Chatter"); //main Frame
    JPanel ctrlPanel = new JPanel(); //Panel to host controls
    JButton btnElect = new JButton("Start Election"); //Election button to manually start election.
    JButton btnDrop = new JButton("Stop/Drop"); //Stop button to stop the process.
    JButton btnRestart = new JButton("Restart"); //Restart button to put process again in ring.
    JTextArea messageArea = new JTextArea(8, 40); //message area to display All relevant messages

    public static Integer startPort = 9260; //Start port num. All process start from this port
    public static String host = "127.0.0.1";
    public static Integer portNo = 0; //Port no based on proc. for proc 2 port = 9262
    public static Integer procNo = 0; //Process number is stored here
    public static Integer Timeout = 8000; //Universal timout value to start election
    public static Integer coOrdId = -1; //Coordinator id is stored here. Initalezed to -1
    public static Boolean isCoordi = false; //Bool flag to check if current proc is coordinator
    public static ServerSocket servSocket; //Server socket
    public static ClientHandler handler = new ClientHandler(); //Handler thread to handle all eletion part
    public static boolean isSuspended = false; //Bool flag to check if Thread is suspended.
    public static Timer clock; //Timer to send Alive messages from Co ordinator to other proc
    public static final Integer Max_Node_Count = 5; //Max allowed count of procsses.

    //Constructor. Mainly used to build UI and add event listners
    public Election() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        messageArea.setEditable(false);
        btnRestart.setEnabled(false);
        ctrlPanel.setLayout(new FlowLayout()); //Laypot is set here
        //Controls added to panel
        ctrlPanel.add(btnElect);
        ctrlPanel.add(btnDrop);
        ctrlPanel.add(btnRestart);
        //Frame properties set
        frame.setLayout(new GridLayout(2, 1));
        frame.getContentPane().add(new JScrollPane(messageArea), "Center");
        frame.add(ctrlPanel);
        frame.pack();

        //Adding event listner to Elect Button
        btnElect.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                //Starting the Election
                handler.startElectionProcess();
            }
        });

        //Adding event listner to Stop Button
        btnDrop.addActionListener(new ActionListener() {
            @Override
            @SuppressWarnings("deprecation")
            public void actionPerformed(ActionEvent e) {
                //Stopping the process
                handler.dropTheProcess();
                try {
                    //Stopping clock.
                    //If not stopped , thread cant be suspended.
                    if (clock != null) {
                        clock.cancel();
                    }
                    handler.suspend(); //Thread is suspended.
                    isSuspended = true;
                    isCoordi = false;
                    servSocket.close(); //Socket is cloed to stop further communication
                    messageArea.append("Process Stopped!! \n");

                    //Buttons are enabled/Disabled based on state
                    btnRestart.setEnabled(true);
                    btnElect.setEnabled(false);
                } catch (Exception ex) {  // Catch actions if not crashed
                    ex.printStackTrace();
                    messageArea.append("Unable to stop this process.\n");
                }
            }
        });

        btnRestart.addActionListener(new ActionListener() {
            @Override
            @SuppressWarnings("deprecation")
            public void actionPerformed(ActionEvent e) {
                //handler.restartProcess();

                try {
                    //Resatarting the process.
                    //New socket connection is opened from same previous port
                    servSocket = new ServerSocket(portNo);
                    //new socket object is passed onto handler thread.
                    handler.resetServSoc(servSocket);
                    handler.resume();
                    isSuspended = false;
                    messageArea.append("Process restarted!! \n");
                    handler.startElectionProcess();

                    btnRestart.setEnabled(false);
                    btnElect.setEnabled(true);
                } catch (Exception e1) {
                    e1.printStackTrace();
                    messageArea.append("Unable to restart this process.\n");
                }
            }
        });
    }

    public static void main(String[] args) throws Exception {
        Election cl = new Election();
        cl.frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        cl.frame.setVisible(true);

        //Iterating over the available ports to connect to open port.
        for (int i = 0; i < Max_Node_Count; i++) {
            ServerSocket newSoc;
            portNo = startPort + i;
            try {
                newSoc = new ServerSocket(portNo);
                procNo = portNo - startPort;
                newSoc.close();
                break;
            } catch (Exception e) {
                System.out.println("Port no " + portNo + " already in use");
            }
        }
        try {
            //based on th open port which we get from above code is used to open socket.
            servSocket = new ServerSocket(portNo);
            //servSocket.setSoTimeout(100);
            System.out.println("Connected to port: " + portNo + " with Process Id: " + procNo);
            cl.frame.setTitle("Node: " + procNo);
            setTimeout(procNo);
            handler.start(); // Start the thread
            handler.init(cl, servSocket); // inform the initial variables
            if (procNo == Max_Node_Count - 1) {
                //If this is the last process, we start Election.
                handler.startElectionProcess();
            }
        } catch (IOException e) {
            System.out.println("Unable to connect to port " + portNo);
            System.exit(1);
        }
    }

    //Individual Timouts for process can be set here.
    //Proc 1 and 3 have same timeout, so the they both can start election at same time.
    public static void setTimeout(int procNo){
        switch (procNo) {
            case 0:
                Timeout = 80000;
                break;
            case 1:
                Timeout = 40000;
                break;
            case 2:
                Timeout = 80000;
                break;
            case 3:
                Timeout = 40000;
                break;
            case 4:
                Timeout = 80000;
                break;
            case 5:
                Timeout = 80000;
                break;
            case 6:
                Timeout = 80000;
                break;
            case 7:
                Timeout = 80000;
                break;
        }
    }

    //main Handler code which contains all methods to start election and staying alive.
    public static class ClientHandler extends Thread {
        public BufferedReader in;
        public PrintWriter out;
        public Election gui;
        public ServerSocket servSoc;
        public Socket client;

        //init function to initialize objects got from main
        public void init(Election gui, ServerSocket servSoc) {

            this.gui = gui; // assign the frame
            this.servSoc = servSoc;     // assign the socket
            gui.messageArea.append("Process Started!! \n");
        }

        //After restarting proc, new Socket obj is assigned using this method
        public void resetServSoc(ServerSocket servSoc) {
            this.servSoc = servSoc;
        }

        //Method call to star t election process.
        public void startElectionProcess() {
            try {
                gui.messageArea.append("Election Starts!! \n");
                gui.messageArea.append(" \n");
                String token = "Election: " + procNo;
                sendElectionMessage(token, procNo + 1);
            } catch (Exception ex) {
                //Skipping the above node as it is unavailable.
                String token = "Election: " + procNo;
                sendElectionMessage(token, procNo + 2);
            }
        }

        //Method to send election messages.
        public void sendElectionMessage(String token, int Next_Process) {
            if (Next_Process > (Max_Node_Count - 1)) {
                Next_Process = Next_Process - Max_Node_Count; //iteration back to start to ame ring
            }
            //System.out.println(token);
            //System.out.println(Next_Process + "   " + (startPort + Next_Process));

            try {
                //Temporary socket created to send message
                Socket client_socket = new Socket(host, startPort + Next_Process);
                PrintWriter out = new PrintWriter(client_socket.getOutputStream());
                out.println(token);
                out.flush();
                out.close();
                client_socket.close();
            } catch (Exception ex) {
                //ex.printStackTrace();
                sendElectionMessage(token, Next_Process + 1);
            } finally {
                gui.messageArea.append("Sent: " + token + "\n");
            }
        }

        public void sendAliveMessageOld() {
            int Next_Process = procNo + 1;
            if (Next_Process > (Max_Node_Count - 1)) {
                Next_Process = Next_Process - Max_Node_Count;
            }
            String token = "Alive from: " + procNo + "\n";

            try {
                Socket client_socket = new Socket(host, startPort + Next_Process);
                PrintWriter out = new PrintWriter(client_socket.getOutputStream());
                out.println(token);
                out.flush();
                out.close();
                client_socket.close();
                gui.messageArea.append("Sent: " + token + "\n");
            } catch (Exception ex) {
                System.out.println("NP: " + Next_Process + " CID: " + coOrdId);
                if (Next_Process == coOrdId) {

                    startElectionProcess();
                }
            }
        }

        public void sendAliveMessage() {
            try {
                clock = new Timer();
                clock.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        try {
                            String token;
                            for (int i = 0; i < procNo; i++) {
                                if (isCoordi) {
                                    token = "Alive from Ring Coordinator("+ procNo +") to Process: " + i + "\n";
                                    Socket client_socket = new Socket(host, startPort + i);
                                    PrintWriter out = new PrintWriter(client_socket.getOutputStream());
                                    out.println(token);
                                    out.flush();
                                    out.close();
                                    client_socket.close();
                                    //gui.messageArea.append("Sent: " + token + "\n");
                                    System.out.println("Sent: " + token);
                                }
                                else{
                                    clock.cancel();
                                }
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }, 0, 4 * 1000);
            } catch (Exception e) {
                e.printStackTrace();
            }

        }

        public void selectCoordiator(Integer coid) {
            //System.out.println("Entered Select cordi func");
            coOrdId = coid;
            //System.out.println(coOrdId + " " + coid);
            gui.messageArea.append("Co-ordinator Elected!!! \n");
            gui.messageArea.append("Coordinator: " + coid + " elected by " + procNo + "\n");

            sendElectionMessage("Coordinator: " + coid + " elected by " + procNo, procNo + 1);
            if (procNo == coid) {
                isCoordi = true;
                gui.messageArea.append("This node is selected as Coordinator. \n");
                sendAliveMessage();
            }
        }

        public void parseInput(String inp, PrintWriter out) throws InterruptedException, IOException {
            //System.out.println("Received: " + inp + "\n");
            //Thread.sleep(procNo * 1000);
            String[] inpArr = inp.split(" ");
            if (inpArr[0].equals("Election:")) {
                gui.messageArea.append("Received: " + inp + "\n");
                Thread.sleep(2000);
                Boolean conts = false;
                for (int i = 0; i < inpArr.length; i++) {
                    if (inpArr[i].equals(procNo.toString())) {
                        conts = true;
                        break;
                    }
                }
                if (conts) {
                    //System.out.println("Came back to start now need to elect");
                    int coid = -1;
                    for (int i = 1; i < inpArr.length; i++) {
                        if (coid < Integer.parseInt(inpArr[i]))
                            coid = Integer.parseInt(inpArr[i]);
                    }
                    selectCoordiator(coid);
                } else {
                    sendElectionMessage(inp + " " + procNo, procNo + 1);
                }
            } else if (inpArr[0].equals("Coordinator:")) {
                gui.messageArea.append("Received: " + inp + "\n");
                coOrdId = Integer.parseInt(inpArr[1]);
                if (isCoordi && !inpArr[1].equals(procNo.toString())) {
                    isCoordi = false;
                    gui.messageArea.append("This node is not a Coordinator anymore. \n");
                    if (clock != null) {
                        clock.cancel();
                    }
                }
                if (!isCoordi && inpArr[1].equals(procNo.toString())) {
                    //System.out.println(isCoordi);
                    //System.out.println(inpArr[1].equals(procNo.toString()) + " " + procNo.toString());
                    isCoordi = true;
                    gui.messageArea.append("This node is selected as Coordinator. \n");
                    sendAliveMessage();
                }
                if (!inpArr[4].equals(procNo.toString())) {
                    sendElectionMessage(inp, procNo + 1);
                }

            } else if (inpArr[0].equals("Alive")) {
                //gui.messageArea.append("Received: " + inp + "\n");
                System.out.println("Received: " + inp );
                try {
                    String token = "Acknowledge from " + procNo;

                    Socket client_socket = new Socket(host, startPort + coOrdId);
                    PrintWriter outx = new PrintWriter(client_socket.getOutputStream());
                    outx.println(token);
                    outx.flush();
                    outx.close();
                    client_socket.close();
                    //gui.messageArea.append("Sent: " + token + "\n");
                    System.out.println("Sent: " + token );
                } catch (Exception e) {
                    e.printStackTrace();
                }
                //sendAliveMessage();
            } else if (inpArr[0].equals("Acknowledge")) {
                System.out.println("Received: " + inp );
            }
        }

        public void dropTheProcess() {
            try {
                servSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void restartProcess() {
            try {
                servSocket = new ServerSocket(portNo);
                System.out.println("Connected to port: " + portNo + " with Process Id: " + procNo);
                servSoc = servSocket;
                startElectionProcess();
                this.run();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void reconnectnElect() {
            try {
                servSoc.close();
                servSoc = new ServerSocket(portNo);
                startElectionProcess();
                this.run();
            } catch (IOException e) {
                e.printStackTrace();
            }

        }

        public void run() {

            try {
                servSoc.setSoTimeout(Timeout);
                while (true) {
                    client = servSoc.accept();
                    in = new BufferedReader(new InputStreamReader(
                            client.getInputStream()));
                    //System.out.println("system reached here");
                    String input = in.readLine();
                    parseInput(input, out);
                }
            } catch (SocketTimeoutException ex) {
                System.out.println("Timeout Occured");
                reconnectnElect();
                //startElectionProcess();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }

}
