package BioHarnessSensor;

import java.util.ArrayList;
import java.util.List;

import zephyr.android.BioHarnessBT.BTClient;
import zephyr.android.BioHarnessBT.ConnectListenerImpl;
import zephyr.android.BioHarnessBT.ConnectedEvent;
import zephyr.android.BioHarnessBT.PacketTypeRequest;
import zephyr.android.BioHarnessBT.ZephyrPacketArgs;
import zephyr.android.BioHarnessBT.ZephyrPacketEvent;
import zephyr.android.BioHarnessBT.ZephyrPacketListener;
import zephyr.android.BioHarnessBT.ZephyrProtocol;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

public class NewConnectedListener extends ConnectListenerImpl
{
	private Handler _OldHandler;
	private Handler _aNewHandler; 
	final int GP_MSG_ID = 0x20;
	final int BREATHING_MSG_ID = 0x21;
	final int ECG_MSG_ID = 0x22;
	final int RtoR_MSG_ID = 0x24;
	final int ACCEL_100mg_MSG_ID = 0x2A;
	final int SUMMARY_MSG_ID = 0x2B;
	
	
	private int GP_HANDLER_ID = 0x20;
	
	
	// stores timestamps
	private List<Integer> herts = new ArrayList<Integer>();
		
	// for converting from signed to unsigned
	public static int unsignedByteToShort(byte b) {
		return b & 0xFF;
	}
	
	private final int HEART_RATE = 0x100;
	private final int RESPIRATION_RATE = 0x101;
	private final int SKIN_TEMPERATURE = 0x102;
	private final int POSTURE = 0x103;
	private final int PEAK_ACCLERATION = 0x104;
	/*Creating the different Objects for different types of Packets*/
	private GeneralPacketInfo GPInfo = new GeneralPacketInfo();
	private ECGPacketInfo ECGInfoPacket = new ECGPacketInfo();
	private BreathingPacketInfo BreathingInfoPacket = new  BreathingPacketInfo();
	private RtoRPacketInfo RtoRInfoPacket = new RtoRPacketInfo();
	private AccelerometerPacketInfo AccInfoPacket = new AccelerometerPacketInfo();
	private SummaryPacketInfo SummaryInfoPacket = new SummaryPacketInfo();
	
	private PacketTypeRequest RqPacketType = new PacketTypeRequest();
	public NewConnectedListener(Handler handler,Handler _NewHandler) {
		super(handler, null);
		_OldHandler= handler;
		_aNewHandler = _NewHandler;

		// TODO Auto-generated constructor stub

	}
	public void Connected(ConnectedEvent<BTClient> eventArgs) {
		System.out.println(String.format("Connected to BioHarness %s.", eventArgs.getSource().getDevice().getName()));
		/*Use this object to enable or disable the different Packet types*/
		RqPacketType.GP_ENABLE = true;
		RqPacketType.BREATHING_ENABLE = true;
		RqPacketType.LOGGING_ENABLE = true;
		
		
		//Creates a new ZephyrProtocol object and passes it the BTComms object
		ZephyrProtocol _protocol = new ZephyrProtocol(eventArgs.getSource().getComms(), RqPacketType);
		//ZephyrProtocol _protocol = new ZephyrProtocol(eventArgs.getSource().getComms(), );
		_protocol.addZephyrPacketEventListener(new ZephyrPacketListener() {
			public void ReceivedPacket(ZephyrPacketEvent eventArgs) {
				ZephyrPacketArgs msg = eventArgs.getPacket();
				byte CRCFailStatus;
				byte RcvdBytes;
				
				
				
				CRCFailStatus = msg.getCRCStatus();
				RcvdBytes = msg.getNumRvcdBytes() ;
				int MsgID = msg.getMsgID();
				byte [] DataArray = msg.getBytes();	
				switch (MsgID)
				{

				case GP_MSG_ID:

				
					//***************Displaying the Heart Rate********************************
					int HRate =  GPInfo.GetHeartRate(DataArray);
					Message text1 = _aNewHandler.obtainMessage(Global.HEART_RATE);
					Bundle b1 = new Bundle();
					b1.putInt("HeartRate", HRate);
					text1.setData(b1);
					_aNewHandler.sendMessage(text1);
//					System.out.println("Heart Rate is "+ HRate);

					//***************Displaying the Respiration Rate********************************
					double RespRate = GPInfo.GetRespirationRate(DataArray);
					
					text1 = _aNewHandler.obtainMessage(Global.RESPIRATION_RATE);
					b1.putFloat("RespirationRate", (float) RespRate);
					text1.setData(b1);
					_aNewHandler.sendMessage(text1);
//					System.out.println("Respiration Rate is "+ RespRate);
					
					//***************Displaying the Skin Temperature*******************************
		

					double SkinTempDbl = GPInfo.GetSkinTemperature(DataArray);
					 text1 = _aNewHandler.obtainMessage(Global.SKIN_TEMPERATURE);
					//Bundle b1 = new Bundle();
					b1.putFloat("SkinTemperature", (float) SkinTempDbl);
					text1.setData(b1);
					_aNewHandler.sendMessage(text1);
//					System.out.println("Skin Temperature is "+ SkinTempDbl);
					
					//***************Displaying the Posture******************************************					

				int PostureInt = GPInfo.GetPosture(DataArray);
				text1 = _aNewHandler.obtainMessage(POSTURE);
				b1.putInt("Posture", PostureInt);
				text1.setData(b1);
				_aNewHandler.sendMessage(text1);
//				System.out.println("Posture is "+ PostureInt);	
				//***************Displaying the Peak Acceleration******************************************

				double PeakAccDbl = GPInfo.GetPeakAcceleration(DataArray);
				text1 = _aNewHandler.obtainMessage(PEAK_ACCLERATION);
				b1.putFloat("PeakAcceleration", (float) PeakAccDbl);
				text1.setData(b1);
				_aNewHandler.sendMessage(text1);
//				System.out.println("Peak Acceleration is "+ PeakAccDbl);	
				
				byte ROGStatus = GPInfo.GetROGStatus(DataArray);
//				System.out.println("ROG Status is "+ ROGStatus);
				
				// HRV
				// variables for computing HRV
				long [] RRs = new long [17];
				long [] RRsDiffs = new long[16];
				long [] sqar = new long[16];
				double sumOfSquares = 0;
				double mean = 0;
				double rmssd = 0;
				
				// get new timestamps from new packet
				for (int i = 14; i >= 0; i--) {
					boolean found = false;
					int timestamp = (unsignedByteToShort(DataArray[12+i*2]) << 8) + unsignedByteToShort(DataArray[11+i*2]);
					for (int j = 0; j < herts.size(); j++)
						if (timestamp == herts.get(j))
							found = true;
					if (!found)
						herts.add(timestamp);
				}
				
				// only want last 18 timestamps
				while (herts.size() > 18)
					herts.remove(0);
				
				// handles situation where timestamp rolls overs
				for (int i = 0; i < herts.size()-1; i++) {
					if (herts.get(i+1) < herts.get(i))
						RRs[i] = 65536 - herts.get(i) + herts.get(i+1);
					else
						RRs[i] = herts.get(i+1) - herts.get(i);
				}

				// computes HRV
				for (int i = 0; i < herts.size()-2; i++)
					RRsDiffs[i] = RRs[i] - RRs[i+1];
				for (int i = 0; i < herts.size()-2; i++)
					sqar[i] = RRsDiffs[i] * RRsDiffs[i];
				for (int i = 0; i < herts.size()-2; i++)
					sumOfSquares += sqar[i];
				mean = sumOfSquares / herts.size()-2;
				rmssd = Math.sqrt(mean);
				
				// sends to handler
				text1 = _aNewHandler.obtainMessage(Global.HRV);
				b1.putInt("RMSSD", (int) rmssd);
				text1.setData(b1);
				_aNewHandler.sendMessage(text1);
				
				
					break;
				case BREATHING_MSG_ID:
					/*Do what you want. Printing Sequence Number for now*/
//					System.out.println("Breathing Packet Sequence Number is "+BreathingInfoPacket.GetSeqNum(DataArray));
					break;
				case ECG_MSG_ID:
					/*Do what you want. Printing Sequence Number for now*/
//					System.out.println("ECG Packet Sequence Number is "+ECGInfoPacket.GetSeqNum(DataArray));
					break;
				case RtoR_MSG_ID:
					/*Do what you want. Printing Sequence Number for now*/
//					System.out.println("R to R Packet Sequence Number is "+RtoRInfoPacket.GetSeqNum(DataArray));
					break;
				case ACCEL_100mg_MSG_ID:
					/*Do what you want. Printing Sequence Number for now*/
//					System.out.println("Accelerometry Packet Sequence Number is "+AccInfoPacket.GetSeqNum(DataArray));
					break;
				case SUMMARY_MSG_ID:
					/*Do what you want. Printing Sequence Number for now*/
//					System.out.println("Summary Packet Sequence Number is "+SummaryInfoPacket.GetSeqNum(DataArray));
					break;
					
				}
			}
		});
	}
	
}