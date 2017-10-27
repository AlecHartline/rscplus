package Client;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Random;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Sequence;
import javax.sound.midi.Sequencer;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;
import javax.sound.midi.Transmitter;

import Game.Reflection;


public class MidiHandler extends Thread{
	
	public byte max_volume = 10;

	Object player_object;
	public void run() {
		try {
			Sequencer sequencer = MidiSystem.getSequencer();
			
			if(sequencer == null) {
				Logger.Error("Midi Squencer device not supported!");
				return;
			}

			int lastRegion = getRegionID();
			loadSong(sequencer, lastRegion);
			
			sequencer.open();
			sequencer.start();
			
			while(true) {
				//System.out.printf("Tick: %d, Length: %d, RegionID: %d%n", sequencer.getTickPosition(), sequencer.getTickLength(), lastRegion);

				try {
					JClassLoader classLoader = Launcher.getInstance().getClassLoader();
					Class<?> c = classLoader.loadClass("da");
					for(Field f : c.getDeclaredFields()) {
						System.out.println(f.toGenericString() + ","+f.getGenericType());
						if(f.getGenericType().toString().equals("int")) {
							f.setAccessible(true);
							int a = 0;
							System.out.println(f.getInt(a));
						}
					}
				} catch (ClassNotFoundException | IllegalAccessException  e) {e.printStackTrace();}
				catch (IllegalArgumentException e) {}
				if(lastRegion != getRegionID() || sequencer.getTickPosition() == sequencer.getTickLength()) {
					log(String.format("Detected regional variance in %d, now %d. Fading song for %d.", lastRegion, getRegionID(), lastRegion));
					lastRegion = getRegionID();
					
					midiFadeOut(sequencer, 3000);
					log(String.format("Fade-out of song for region %d succeeded.", lastRegion));
					
					loadSong(sequencer, lastRegion);
					log(String.format("Loading of song for region %d succeeded.", lastRegion));
					
					sequencer.open();
					sequencer.start();
					
					log(String.format("Fading in song for region %d.", lastRegion));
					midiFadeIn(sequencer, 3000);
					log(String.format("Fade-in of song for region %d succeeded.", lastRegion));
					
				}
				
				Thread.sleep(1000);
			}
		} catch (MidiUnavailableException | InvalidMidiDataException | IOException | InterruptedException e) {
			e.printStackTrace();
		}
		
	}
	
	private void log(String string) {
		Logger.Info("[MIDI_HANDLER]: "+string);
	}

	private void loadSong(Sequencer sequencer, int lastRegion) throws InvalidMidiDataException, IOException {
		Sequence sequence = MidiSystem.getSequence(new File("assets/mid/"+getSongID(lastRegion)+".mid"));
		sequencer.setSequence(sequence);
		log("Loading song assets/mid/"+getSongID(lastRegion)+".mid");
		try {
			setGlobalVolume(sequence);
		} catch (ArrayIndexOutOfBoundsException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (MidiUnavailableException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}

	private void midiFadeOut(Sequencer sequencer, int durationOfFade) throws MidiUnavailableException, InvalidMidiDataException, InterruptedException {
		
		int millisecondsBetweenDecrement = durationOfFade / Math.abs(max_volume);
		int delay = 0;
		
		for(byte gain = (byte) Math.abs(max_volume); gain > 0; --gain) {
			setIntantaneousVolume(sequencer, gain);
			Thread.sleep(millisecondsBetweenDecrement);
		}
		
		//Ensure all the messages are read before terminating the song
		Thread.sleep(durationOfFade + delay);
		sequencer.stop();
		sequencer.close();
		
	}

	private void midiFadeIn(Sequencer sequencer, int durationOfFade) throws MidiUnavailableException, InvalidMidiDataException, InterruptedException {
		
		int millisecondsBetweenDecrement = durationOfFade / Math.abs(max_volume);
		
		for(byte gain = 0; gain < Math.abs(max_volume); ++gain) {
			setIntantaneousVolume(sequencer, gain);
			Thread.sleep(millisecondsBetweenDecrement);
		}
		
	}

	private void setIntantaneousVolume(Sequencer sequencer, byte gain) throws MidiUnavailableException, InvalidMidiDataException {
		for(byte channel = 0; channel < 15; ++channel) {
			
			byte statusByte = (byte) (0xb0 | channel); 	//append the channel (0-15) to 1011
			byte controlByte = 0x07;					//Code 07 is volume control
			byte volumeByte = (byte) Math.abs(gain); 	//Truncates sign bit
			
			ShortMessage m = new ShortMessage(statusByte, controlByte, Math.min(volumeByte, max_volume));
			
			for(Transmitter t : sequencer.getTransmitters()) {
				t.getReceiver().send(m, sequencer.getTickPosition());
			}
		}
	}
	/**Sets the volume for every note of the entire MIDI.
	 * It iterates through every track of the MIDI, grabs every
	 * MIDI-message, checks if the message is a control-statement (0xB0 | channel)
	 * and if so it will apply min(maxGainValue, currentVolumeOfNote).
	 * It should never throw InvalidMidiDataException
	 * 
	 * @param sequence
	 * @throws InvalidMidiDataException 
	 * @throws MidiUnavailableException 
	 * @throws ArrayIndexOutOfBoundsException 
	 */
	private void setGlobalVolume(Sequence sequence) throws InvalidMidiDataException, ArrayIndexOutOfBoundsException, MidiUnavailableException {
		
		long originalPosition = -1;
		Sequencer sequencer = MidiSystem.getSequencer();
		if(sequencer.getTickPosition() != 0)
			originalPosition = sequencer.getTickPosition();
		
		for(Track track : sequence.getTracks()) {
			//Track is not iteratable
			for(int i = 0; i < track.size(); ++i) {
				MidiMessage message = track.get(i).getMessage();
				if(message instanceof ShortMessage) {
					ShortMessage sm = (ShortMessage) message;
					int command = sm.getCommand();
					if(command == 0xB0 && sm.getData1() == 0x07){ //Check if command is a control-statement for Velocity
						int channel = sm.getChannel();
						int operationCode = sm.getData1(); //Set velocity
						int velocity = Math.min(sm.getData2(), max_volume);
						sm.setMessage(command, channel, operationCode, velocity);
					}
				}
			}
		}
		
		if(originalPosition != -1) {
			sequencer.setTickPosition(originalPosition);
		}
		
	}
	
	private int getRegionID() {
		int x = Game.Client.regionX;
		int y = Game.Client.regionY;
		
		if(x == -1 && y == -1) {
			return 0;
		}
		/* Coordinate box of Falador*/
		if(x >= 192 && y <= 528 && y >= 480 && x <= 288) {
			return 1;
		}
		
		/* Coordinate box of Port Sarim (north) */
		if(x >= 192 && y >= 576 && x <= 240 && y <= 624)
			return 2;
		if(x == 144 && y == 3312)
			return 380;
		return -1;
		
	}
	
	private int getSongID(int input) {
		Random rng = new Random();
		switch(input) {
		case 0:
			return 0;
		case 1:
			switch(rng.nextInt(3)) {
			case 2:
				return 72; //Fanfare
			case 1:
				return 54; //Scape Soft
			case 0:
				return 127;//Nightfall
			}
		case 2:
			switch(rng.nextInt(4)) {
			case 3: return 719;
			case 2: return 210;
			case 1: return 92;
			case 0: return 35;
			}
		case 380:
			switch(rng.nextInt(4)) {
			case 3: return 344;
			case 2: return 11;
			case 1: return 333;
			case 0: return 380;
			}
		default: 
			switch(rng.nextInt(4)) {
			case 3: return 496;
			case 2: return 91; //Riverside (Port Tyras)
			case 1: return 80;
			case 0: return 3;
			}
		}
		return 3;  //Unknown Land
	}
}
