package edu.buffalo.cse.cse486586.simpledht;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Formatter;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.MergeCursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.os.AsyncTask;
import android.telephony.TelephonyManager;
import android.util.Log;

import static android.content.ContentValues.TAG;

public class SimpleDhtProvider extends ContentProvider {

    // Contacts Table Columns names
    private static final String KEY = "key";
    private static final String VALUE = "value";
    private static final String TABLENAME = "MyTable";

    private static final String NODEJOIN = "NodeJoin";
    private static final String NODEINFOUPDATE = "NodeInfoUpdate";

    private static final String INSERTPASS = "InsertPass";

    private static final String SINGLEQUERYPASS = "SingleQueryPass";
    private static final String SINGLEQUERYREPLY = "SingleQueryReply";

    private static final String ATQUERYPASS = "@QueryPass";
    private static final String ATQUERYREPLY = "@QueryReply";

    private static final String SINGLEDELETEPASS = "SingleDeletePass";
    private static final String ATDELETEPASS = "@DeletePass";

    private Databasehelper m_helper;
    static final int SERVER_PORT = 10000;
    static final int REMOTE_PORT = 11108;

    boolean IsCurrentNodeTopNodeInChord = false;
    boolean IsNodeInfoMutlicastToBeStopped =  false;
    boolean IsNodeJoinCompleted = false;
    boolean IsReturnCursorChanged = false;

    Cursor  m_ReturnCursor = null;


    class Node implements Comparable<Node> {

        public String HashedDeivceID ="";
        public int PortNumber=0;

        Node(String HashedDeivceID, int PortNumber) {
            this.HashedDeivceID = HashedDeivceID;
            this.PortNumber = PortNumber;
        }

        public int compareTo(Node node) {
            if (this.HashedDeivceID.compareTo(node.HashedDeivceID) <= 0) {
                return -1;
            } else {
                return 1;
            }

        }

    }
    public Node m_PredecessorNode ;
    public Node m_SuccessorNode ;
    public Node m_currentNode;

    public ArrayList<Node> m_NodeJoinList = new ArrayList<Node>();


    public class Databasehelper extends SQLiteOpenHelper {

        public Databasehelper(Context context) {
            super(context, "MyDatabase", null, 2);
            Log.e("db:", "Databasehandler Constructor");
        }

        // Creating Tables
        @Override
        public void onCreate(SQLiteDatabase db) {
            Log.v("db:", "Database Create");
            String QUERY = "CREATE TABLE " + TABLENAME + "("
                    + KEY + " TEXT PRIMARY KEY,"
                    + VALUE + " TEXT" + ")";
            db.execSQL(QUERY);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            // Drop older table if existed
            db.execSQL("DROP TABLE IF EXISTS " + "Table");
            // Create tables again
            onCreate(db);
        }


        public void Insert(ContentValues values) {
            Log.v("content insert", "enter");
            String key = values.getAsString("key");
            String Msg = values.getAsString("value");

            String QUERY = "INSERT OR REPLACE INTO MyTable (key,value) VALUES(\"" + key + "\",\"" + Msg + "\")";
            Log.v("content insert", QUERY);
            getWritableDatabase().execSQL(QUERY);


        }

        public void Delete(String selection)
        {
            SQLiteQueryBuilder qbuild = new SQLiteQueryBuilder();
            qbuild.setTables(TABLENAME);
            Log.e(TAG, "delete enter");
            String Query = "";
            if(selection.compareTo("@")==0 || selection.compareTo("*")==0) {
                Query = "DELETE from MyTable";
            }
            else
            {
                Query = "DELETE from MyTable WHERE key =" +"\""+ selection+"\"";
            }
            SQLiteDatabase db =getWritableDatabase();
            db.execSQL(Query);
            db.close();
        }

        public Cursor Query(String selection)
        {
            Log.e(TAG,"main Query Enter --check1");
            SQLiteQueryBuilder qbuild = new SQLiteQueryBuilder();
            qbuild.setTables(TABLENAME);

            String Query = "";

            if(selection.compareTo("@")==0 || selection.compareTo("*")==0)
            {
                Query = "SELECT * from MyTable";
                Log.e(TAG,"main Query Enter --check2");
            }
            else
            {
                Query = "SELECT * from MyTable WHERE key =" +"\""+ selection+"\"";
                Log.e(TAG,"main Query Enter --check3");
            }

            Log.e(TAG,"main Query Enter --check4");
            Cursor cursor = getReadableDatabase().rawQuery(Query, null);
            Log.e(TAG ,"cursor value needed="+String.valueOf(cursor.getCount()));
            return cursor;
        }

    }


    public boolean doesKeyBelongToSelfChordRegion(String HashedKey)
    {

        if(!IsCurrentNodeTopNodeInChord )
        {
            Log.e(TAG, "Predecessor"+m_PredecessorNode.HashedDeivceID);
            Log.e(TAG, "HashedKey"+HashedKey);
            Log.e(TAG, "Current"+m_currentNode.HashedDeivceID+"\n");

            boolean flag1 = HashedKey.compareTo(m_PredecessorNode.HashedDeivceID) > 0 && HashedKey.compareTo(m_currentNode.HashedDeivceID) <= 0;
            Log.e(TAG,"flag"+String.valueOf(flag1));
            return flag1;
        }
        else
        {
            Log.e(TAG, "Predecessor"+m_PredecessorNode.HashedDeivceID);
            Log.e(TAG, "HashedKey"+HashedKey);
            Log.e(TAG, "Current"+m_currentNode.HashedDeivceID+"\n");

            boolean flag2 = HashedKey.compareTo(m_PredecessorNode.HashedDeivceID) < 0 && HashedKey.compareTo(m_currentNode.HashedDeivceID) <= 0;
            boolean flag3 = HashedKey.compareTo(m_PredecessorNode.HashedDeivceID) > 0 && HashedKey.compareTo(m_currentNode.HashedDeivceID) > 0;
            Log.e(TAG,"flag"+String.valueOf(flag2||flag3));
            return (flag2||flag3);
        }

    }

    @Override
    public String getType(Uri uri) {
        // TODO Auto-generated method stub
        return null;
    }


    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        // TODO Auto-generated method stub

        if(!IsNodeJoinCompleted)
        {
            Log.e(TAG,"Deletecheck1");
            m_helper.Delete(selection);
        }
        else {
            if (selection.compareTo("@") == 0) {
                Log.e(TAG, "@ Delete Done");
                m_helper.Delete("@");
            } else if (selection.compareTo("*") == 0) {
                Log.e(TAG, "star Delete Started");
                m_helper.Delete("@");
                String InitiatorNode = String.valueOf(m_currentNode.PortNumber);
                String msgToSend = ATDELETEPASS + ":" + InitiatorNode + "\n";
                SendMethod(msgToSend, m_SuccessorNode.PortNumber);
            } else {
                Log.e(TAG, "Single Delete Started");
                String HashedKey = GenerateHashWrapper(selection);
                validateAndDelete(HashedKey, selection);
            }
        }
        return 0;
    }

    public void validateAndDelete(String HashedKey,String key)
    {
        // TODO Auto-generated method stub
        boolean flag = doesKeyBelongToSelfChordRegion(HashedKey);
        if(flag)
        {
            Log.e(TAG, "single delete done");
            m_helper.Delete(key);
        }
        else {
            Log.e(TAG, "deletepass");
            String msgToSend = SINGLEDELETEPASS + ":" + HashedKey + ":" + key + "\n";
            SendMethod(msgToSend, m_SuccessorNode.PortNumber);
        }

    }

    @Override
    public Uri insert(Uri uri, ContentValues values)
    {
        if(!IsNodeJoinCompleted)
        {
            Log.e(TAG,"insertcheck1");
            m_helper.Insert(values);
        }
        else
        {
            Log.e(TAG,"insertcheck2");
            IsNodeInfoMutlicastToBeStopped =  true;
            String HashedKey = "";
            String key = values.getAsString("key");
            String val = values.getAsString("value");
            HashedKey = GenerateHashWrapper(key);
            validateAndInsert(HashedKey,key,val);
        }
        return uri;
    }

    public void validateAndInsert(String HashedKey,String key,String val)
    {
       // TODO Auto-generated method stub
       boolean flag = doesKeyBelongToSelfChordRegion(HashedKey);
       if(flag)
       {
           Log.e(TAG,"insertself");
           ContentValues values = new ContentValues();
           values.put("key",key);
           values.put("value",val);
           Log.v("insert", values.toString());
           m_helper.Insert(values);
       }
       else
       {
           Log.e(TAG,"insertpass");
           String msgToSend = INSERTPASS+":"+HashedKey+":"+key+":"+val+"\n";
           SendMethod(msgToSend,m_SuccessorNode.PortNumber);
       }

   }


    public String ExtractValuesFromCursor(Cursor cursor)
    {


        Log.e(TAG,"ExtractValuesFromCursor cnt = "+String.valueOf(cursor.getCount()));
        StringBuilder builder = new StringBuilder();
        try
        {

            while (cursor.moveToNext())
            {
                int keyIndex = cursor.getColumnIndex("key");
                int valueIndex = cursor.getColumnIndex("value");
                String returnKey = cursor.getString(keyIndex);
                String returnValue = cursor.getString(valueIndex);
                builder.append(returnKey);
                builder.append("#");
                builder.append(returnValue);
                if(!cursor.isLast())
                {
                    builder.append("#");
                }
            }
        }
        finally
        {
            cursor.close();
        }
        Log.e(TAG,"ExtractValuesFromCursor --- END");
        return builder.toString();
    }


    public Cursor PutStringValuesIntoCursor(String inputstr ,Cursor currentnodecursor)
    {

        MatrixCursor matrixCursor = new MatrixCursor(new String[] { "key", "value" });
        Log.e(TAG,"PutStringValuesIntoCursor---inputstr = "+inputstr);


        if(!inputstr.isEmpty()) {

            String[] arr = inputstr.split("#");
            Log.e(TAG, "PutStringValuesIntoCursor---arr len  = " + String.valueOf(arr.length));
            for (int i = 0; i <= arr.length - 2; i = i + 2) {
                matrixCursor.addRow(new Object[]{arr[i], arr[i + 1]});
            }
        }
        MergeCursor mergeCursor = new MergeCursor(new Cursor[]{matrixCursor,currentnodecursor});

        Log.e(TAG,"PutStringValuesIntoCursor---mergeCursor len  = "+String.valueOf(mergeCursor.getCount()));
        return mergeCursor;
    }


    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {
        // TODO Auto-generated method stub

        Log.e(TAG,"Query selection = "+selection);
        if(!IsNodeJoinCompleted)
        {
            Log.e(TAG,"queryself");
            m_ReturnCursor = m_helper.Query(selection);
            IsReturnCursorChanged = true;
        }
        else
        {
            String InitiatorNode = String.valueOf(m_currentNode.PortNumber);
            Log.e(TAG,"querycheck2");
            String HashedKey = "";

            if(selection.compareTo("@")==0)
            {
                m_ReturnCursor = m_helper.Query(selection);
                IsReturnCursorChanged = true;
            }
            else if(selection.compareTo("*")==0)
            {
                String msgToSend ="";
                msgToSend = ATQUERYPASS+":"+InitiatorNode+"\n";
                SendMethod(msgToSend, m_SuccessorNode.PortNumber);
            }
            else
            {
                HashedKey = GenerateHashWrapper(selection);
                validateAndQuery(selection,HashedKey,InitiatorNode);
            }

        }
        Log.v("query", selection);

        while(IsReturnCursorChanged == false)
        {
            //Wait Until Return Cursor is filled
        }

        IsReturnCursorChanged = false;
        return m_ReturnCursor;
    }


    public void validateAndQuery(String selection,String HashedKey,String QueryInitiatorNode)
    {
        // TODO Auto-generated method stub
        String msgToSend ="";
        boolean flag = doesKeyBelongToSelfChordRegion(HashedKey);
        if(flag)
        {
            Cursor cursor = m_helper.Query(selection);
            String str = ExtractValuesFromCursor(cursor);
            msgToSend = SINGLEQUERYREPLY+":"+str+":"+QueryInitiatorNode+"\n";
            Log.e(TAG,"validateAndQuery if:"+msgToSend);
            SendMethod(msgToSend,m_PredecessorNode.PortNumber);
        }
        else
        {
            Log.e(TAG,"validateAndQuery else:"+msgToSend);
            msgToSend = SINGLEQUERYPASS+":"+selection+":"+HashedKey+":"+QueryInitiatorNode+"\n";
            SendMethod(msgToSend,m_SuccessorNode.PortNumber);
        }
    }

    @Override
    public boolean onCreate() {
        // TODO Auto-generated method stub
        TelephonyManager tel = (TelephonyManager) this.getContext().getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        String SelfPortNumber = String.valueOf((Integer.parseInt(portStr) * 2));

        m_currentNode = new Node(GenerateHashWrapper(portStr), Integer.parseInt(SelfPortNumber));
        Log.e(TAG, SelfPortNumber);
        try
        {
            Context context = getContext();
            m_helper = new Databasehelper(context);
            Log.v("DB", "Databasehelper created");
        }
        catch (Exception e)
        {
            Log.v("DB", "Databasehelper is not created.Exception Occurred");
        }

        try
        {
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
            new NodeJoinClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, SelfPortNumber);

        } catch (IOException e) {
            Log.e(TAG, "Can't create a ServerSocket");

        }
        return false;
    }



    public Void SendMethod(String msgToSend,int PortNumber)
    {

        try
        {
            Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),PortNumber);
            Log.e(TAG, "SendMethod" +String.valueOf(PortNumber));


            Log.e(TAG, "Check1");
            OutputStreamWriter oswriter = new OutputStreamWriter(socket.getOutputStream());
            BufferedWriter bwriter = new BufferedWriter(oswriter);
            bwriter.write(msgToSend);
            bwriter.flush();


            Log.e(TAG, "Check2");
            InputStreamReader isreader = new InputStreamReader(socket.getInputStream());
            BufferedReader breader = new BufferedReader(isreader);
            String ackstring = breader.readLine();
            if (ackstring != null && ackstring.equals("PA3-OK")) {
                Log.e(TAG, "PA3-OK received");
            }
            bwriter.close();
            breader.close();
            socket.close();
        }catch(UnknownHostException e){
            Log.e(TAG, "ClientTask UnknownHostException");
        }catch(IOException e){

        }
        return null;
    }

    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {

        protected Void doInBackground(ServerSocket... sockets) {
            Log.e(TAG, "ServerTask Enter");
            ServerSocket serverSocket = sockets[0];
            /*
             * TODO: Fill in your server code that receives messages and passes them
             * to onProgressUpdate().
             */
            while (true)
            {
                Log.e(TAG, "Serve0r Task in Before While");
                boolean isConnectionAccepted = false ;
                try {
                    Socket soc = serverSocket.accept();
                    isConnectionAccepted = true;
                    InputStreamReader isreader = new InputStreamReader(soc.getInputStream());
                    BufferedReader breader = new BufferedReader(isreader);
                    String datastr = breader.readLine();

                    Log.e(TAG,datastr);

                    OutputStreamWriter oswriter = new OutputStreamWriter(soc.getOutputStream());
                    BufferedWriter bwriter = new BufferedWriter(oswriter);
                    bwriter.write("PA3-OK");
                    bwriter.flush();

                    bwriter.close();
                    breader.close();
                    soc.close();

                    if (datastr != null)
                    {
                        Log.e(TAG, "Server Task in While");
                        datastr = datastr.replace("\n", "");
                        String[] arr = datastr.split(":");

                        int deviceid = 0;
                        if (arr.length == 2 && arr[0].compareTo(NODEJOIN) == 0)
                        {

                            Log.e(TAG, "ServerTask Check1");
                            deviceid = (Integer.parseInt(arr[1])) / 2;
                            String hashedPortStr = "";

                            hashedPortStr = GenerateHashWrapper(String.valueOf(deviceid));
                            Log.e(TAG, "Mapping of HashedPort and DeviceId = " + hashedPortStr + ":" + String.valueOf(deviceid));

                            m_NodeJoinList.add(new Node(hashedPortStr, deviceid * 2));
                            if(m_NodeJoinList.size() >1)
                            {
                                IsNodeJoinCompleted = true;
                            }
                            Collections.sort(m_NodeJoinList);

                        }
                        else if (arr.length == 5 &&  arr[0].compareTo(NODEINFOUPDATE) == 0)
                        {

                            Log.e(TAG, "ServerTask Check2");
                            m_PredecessorNode = new Node(arr[1],Integer.parseInt(arr[2]));
                            m_SuccessorNode   = new Node(arr[3],Integer.parseInt(arr[4]));

                            Log.e(TAG, "m_PredecessorNodePortNumber = " + m_PredecessorNode.HashedDeivceID+":"+String.valueOf(m_PredecessorNode.PortNumber));
                            Log.e(TAG, "m_SuccessorNodePortNumber = " + m_SuccessorNode.HashedDeivceID+":"+String.valueOf(m_SuccessorNode.PortNumber));

                            IsNodeJoinCompleted = true;

                            if(m_currentNode.HashedDeivceID.compareTo(m_PredecessorNode.HashedDeivceID)<0)
                            {
                                IsCurrentNodeTopNodeInChord = true;
                            }
                            else
                            {
                                IsCurrentNodeTopNodeInChord = false;
                            }

                        }
                        else if(arr.length == 4 && arr[0].compareTo(INSERTPASS) == 0)
                        {
                            Log.e(TAG, "INSERTPASS");
                            validateAndInsert(arr[1], arr[2],arr[3]);
                        }
                        else if(arr.length == 4 && arr[0].compareTo(SINGLEQUERYPASS) == 0)
                        {
                            Log.e(TAG, "SINGLEQUERYPASS");
                            validateAndQuery(arr[1], arr[2],arr[3]);
                        }
                        else if(arr.length == 3 &&arr[0].compareTo(SINGLEQUERYREPLY) == 0)
                        {

                            Log.e(TAG, "SINGLEQUERYREPLY " +arr[2]);
                            if(arr[2].compareTo(String.valueOf(m_currentNode.PortNumber))==0)
                            {

                                Log.e(TAG, "SINGLEQUERYREPLY -- IF");
                                String[] strarr = arr[1].split("#");
                                MatrixCursor matrixCursor =new MatrixCursor(new String[]{"key","value"});
                                matrixCursor.addRow(new Object[] { strarr[0], strarr[1]});
                                m_ReturnCursor = matrixCursor;
                                IsReturnCursorChanged = true;
                                Log.e(TAG,"count of m_ReturnCursor ="+String.valueOf(m_ReturnCursor.getCount()));
                            }
                            else
                            {
                                Log.e(TAG, "SINGLEQUERYREPLY -- ELSE");
                                String msgToSend = SINGLEQUERYREPLY+":"+arr[1]+":"+arr[2]+"\n";
                                SendMethod(msgToSend,m_PredecessorNode.PortNumber);
                            }

                        }
                        else if(arr.length == 2 && arr[0].compareTo(ATQUERYPASS)== 0)
                        {

                            String msgToSend = "";
                            if(arr[1].compareTo(String.valueOf(m_SuccessorNode.PortNumber))!=0)
                            {
                                Log.e(TAG, "ATQUERYPASS -- IF");
                                msgToSend = ATQUERYPASS+":"+arr[1]+"\n";
                                SendMethod(msgToSend, m_SuccessorNode.PortNumber);
                            }
                            else
                            {
                                Log.e(TAG, "ATQUERYPASS -- ELSE");
                                Cursor cursor = m_helper.Query("@");
                                Log.e(TAG, "ATQUERYPASS -- ELSE Cursor Count = "+String.valueOf(cursor.getCount()));
                                String str = ExtractValuesFromCursor(cursor);
                                msgToSend = ATQUERYREPLY+":"+str+":"+arr[1]+"\n";
                                SendMethod(msgToSend, m_PredecessorNode.PortNumber);

                            }
                        }
                        else if(arr.length == 3 && arr[0].compareTo(ATQUERYREPLY)==0)
                        {

                            Cursor cursor = m_helper.Query("@");
                            Log.e(TAG, "ATQUERYREPLY --Cursor Count = "+String.valueOf(cursor.getCount()));

                            if((arr[2].compareTo(String.valueOf(m_currentNode.PortNumber))==0))
                            {
                                Log.e(TAG, "ATQUERYREPLY -- IF");
                                m_ReturnCursor = PutStringValuesIntoCursor(arr[1],cursor);
                                IsReturnCursorChanged = true;

                            }
                            else
                            {
                                Log.e(TAG, "ATQUERYREPLY -- ELSE");
                                String str = ExtractValuesFromCursor(cursor);


                                StringBuilder msg= new StringBuilder();
                                if(str.isEmpty())
                                {
                                    msg.append(arr[1]);
                                }
                                else if(arr[1].isEmpty())
                                {
                                    msg.append(str);
                                }
                                else if(!str.isEmpty() && !arr[1].isEmpty())
                                {
                                    msg.append(arr[1]);
                                    msg.append("#");
                                    msg.append(str);
                                }
                                String msgToSend = ATQUERYREPLY+":"+msg.toString()+":"+arr[2]+"\n";
                                SendMethod(msgToSend, m_PredecessorNode.PortNumber);

                            }

                        }
                        else if(arr.length == 3 &&arr[0].compareTo(SINGLEDELETEPASS)==0)
                        {
                            validateAndDelete(arr[1],arr[2]);
                        }
                        else if(arr.length == 2 &&arr[0].compareTo(ATDELETEPASS)==0)
                        {

                            m_helper.Delete("@");
                            if(arr[1].compareTo(String.valueOf(m_SuccessorNode.PortNumber))!=0)
                            {
                                String msgToSend = ATDELETEPASS+":"+arr[1]+"\n";
                                SendMethod(msgToSend, m_SuccessorNode.PortNumber);

                            }

                        }

                    }
                    else
                    {
                        Log.e(TAG, "Data is not received");
                    }
                }
                catch (IOException e)
                {
                    Log.e(TAG, "Server Task socket IOException");
                }

                if(isConnectionAccepted && !IsNodeInfoMutlicastToBeStopped)
                {
                    Log.e(TAG, "ServerTask Mutitask check");
                    MulticastNodeInfoTask();
                }
            }
        }



        private Void MulticastNodeInfoTask()
        {

                Log.e(TAG, "MulticastNodeInfo Enter");
                if (!m_NodeJoinList.isEmpty()) {

                    for (int j = 0; j < m_NodeJoinList.size(); ++j)
                    {

                            Log.e(TAG, "loop port number " + String.valueOf(j) + ":" + String.valueOf(m_NodeJoinList.get(j).PortNumber));
                            String predecessor = " ";
                            String successor = " ";
                            int predecessorport = 0 ;
                            int successorport = 0;

                            if (j == 0)
                            {
                                predecessor = m_NodeJoinList.get((m_NodeJoinList.size() - 1)).HashedDeivceID;
                                predecessorport = m_NodeJoinList.get((m_NodeJoinList.size() - 1)).PortNumber;
                            }
                            else
                            {
                                predecessor = m_NodeJoinList.get(j - 1).HashedDeivceID;
                                predecessorport = m_NodeJoinList.get(j - 1).PortNumber;
                            }
                            if (j == m_NodeJoinList.size() - 1) {
                                successor = m_NodeJoinList.get(0).HashedDeivceID;
                                successorport = m_NodeJoinList.get(0).PortNumber;
                            } else {
                                successor = m_NodeJoinList.get(j + 1).HashedDeivceID;
                                successorport = m_NodeJoinList.get(j + 1).PortNumber;
                            }


                            if (REMOTE_PORT == m_NodeJoinList.get(j).PortNumber)
                            {
                                m_PredecessorNode = new Node(predecessor,predecessorport);
                                m_SuccessorNode = new Node(successor,successorport);
                                Log.e(TAG, "m_PredecessorNodePortNumber = " + m_PredecessorNode.HashedDeivceID+":"+String.valueOf(m_PredecessorNode.PortNumber));
                                Log.e(TAG, "m_SuccessorNodePortNumber = " + m_SuccessorNode.HashedDeivceID+":"+String.valueOf(m_SuccessorNode.PortNumber));
                            }
                            else
                            {

                                String msgToSend = NODEINFOUPDATE + ":" + predecessor +":"+String.valueOf(predecessorport)+":"+ successor +":"+String.valueOf(successorport)+ "\n";
                                SendMethod(msgToSend,m_NodeJoinList.get(j).PortNumber);

                            }

                    }

                }
                return null;
            }


      }


    private class NodeJoinClientTask extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... msgs)
        {
            Log.e(TAG, "ClientTask check1");
            String msgToSend = NODEJOIN + ":"+String.valueOf(m_currentNode.PortNumber) +"\n";
            Log.e(TAG, "ClientTask check2");
            SendMethod(msgToSend,REMOTE_PORT);
            return null;
        }
    }

    private String GenerateHashWrapper(String input)
    {
        String hashstr = "";
        try
        {
            hashstr = genHash(input);
        }
        catch (NoSuchAlgorithmException e)
        {
            Log.e(TAG,"NoSuchAlgoritmException");
        }
        return hashstr;
    }

    private String genHash(String input) throws NoSuchAlgorithmException {
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        byte[] sha1Hash = sha1.digest(input.getBytes());
        Formatter formatter = new Formatter();
        for (byte b : sha1Hash) {
            formatter.format("%02x", b);
        }
        return formatter.toString();
    }

    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        // TODO Auto-generated method stub
        return 0;
    }

}
