package explorator;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.util.*;

import Actionners.*;
import Representation.Scene;
import envStructures.*;
/**
 * 
 * @author Clara Rigaud 
 * @author Jehyankaa Jeyarajaratnam
 * @author Asma BRAZI
 **
 */
public class Exploration {
	private int explorationMode;
	private static boolean windows;
	public String localURL = "";
	private static boolean deviceConnected;
	private PiThymioRobot robot;
	private InternalRepresentation env ;
	private Scene jmeApp;
	private ArrayList<HashMap<String, String>> dbObjects=new ArrayList<HashMap<String, String>>();
    private int cpWall=0;
    private boolean targetFound=false;
    //We set the distance with which the robot rolls to 1m. 
	private int distanceRob=1;
	private	Point cornerLeft, cornerRight;
	private int threshClose =150;
	private int distMar=41;
	float lenSeg,x0,y0,x1,y1;
	double robRotation;
	Vec2 robPosition;
	public Exploration() throws InterruptedException{
		File configFile = new File("config.properties");
		try {
			
		    FileReader reader = new FileReader(configFile);
		    Properties props = new Properties();
		    props.load(reader);

		    //Check if the current OS is Windows or not
		    windows = System.getProperty("os.name").contains("Windows");
		    //Check if we want to connect to a raspberry, or if we want to test on a simulation
		    deviceConnected = props.getProperty("deviceConnected").contains("true");
		    explorationMode = Integer.parseInt(props.getProperty("roomType"));
		    System.out.println("Started exploration: \nWindows Os = " + windows + "\nDevice available = " + deviceConnected + "\nExplorationMode = "+ explorationMode);
			if(deviceConnected){
				robot = new PiThymioRobot(props);
			}else{
				robot = new PiThymioRobot(props);
			}
			reader.close();
			this.robot.connect();	
			URI uri;
    		uri = Exploration.class.getProtectionDomain().getCodeSource().getLocation().toURI();
    		List<String> localURLArray = Arrays.asList(uri.toString().split("/")).subList(1, uri.toString().split("/").length-1);
    		this.localURL = URLDecoder.decode(String.join("/", localURLArray), "UTF-8");
    		if(!windows){
    			this.localURL="/"+this.localURL;
    		}
			//read the database of objects, in our case we have three images: platon, pepper and the QR code
			readDBObjects();
			System.out.println("DB created");
			//Init of the JME application, where we will display the environment's construction
			env =  new InternalRepresentation(robot,explorationMode);
			jmeApp = new Scene(env, robot);
			this.jmeApp.start();
			robRotation=this.robot.getRotation();
			robPosition=this.robot.getPosition();
		} catch (IOException | URISyntaxException ex) {
            System.err.print("Exploration() ");
            ex.printStackTrace();
        }
	}

	/**
	 * Starts the exploration
	 * @throws IOException 
	 * @throws InterruptedException 
	 */
	@SuppressWarnings("resource")
	public void start() throws IOException, InterruptedException {
		//We ask the user the name of the object that robot have to search
		Scanner sc = new Scanner(System.in);
		int goal=-1;
		System.out.println("Veuillez saisir le chiffre correspondant à l'objet à trouver parmi la liste suivante:");
		System.out.println("0: QR code");
		//System.out.println("1: Pepper");
		//System.out.println("2: Platon");
		
		String str = sc.nextLine();
		goal=Integer.parseInt(str);
		switch(goal) {
			case 0: System.out.println("Le robot autonome va essayer de trouver: QR code"); break;
			//case 1: System.out.println("Le robot autonome va essayer de trouver: Pepper"); break;
			//case 2: System.out.println("Le robot autonome va essayer de trouver: Platon"); break;
			default: System.out.println("Exit"); break;
		}
		System.out.println("Exploration begins");
		while(!this.targetFound){
			explore(goal);
		}
		System.out.println("Exploration ends");
		//robot.disconnect();
		//System.out.println("done");
	}

	@SuppressWarnings("unused")
	//target is the name of the object we re searching for, currently it may be in[0.png, 1.png or 2.png]
	private int explore(int target){
		    System.out.println("My walls are: "+this.env.getWalls().toString()+"\n0");
			robRotation=this.robot.getRotation();
			robPosition=this.robot.getPosition();
			//Step 0: Capture the visual information

			this.jmeApp.map.setcurrentWall(cpWall);
			List<ArrayList<Float>> data;
			// capture visual information
			data=robot.captureData();
			//System.out.println("Visual information: "+data.toString());

			//retreive information about the object to find
			ArrayList<Float> dimensionsObj=data.get(1);
			//System.out.println("dimensionsObj"+dimensionsObj);
			float distanceToWall=dimensionsObj.get(0);
			float startX=dimensionsObj.get(1);
			float startY =dimensionsObj.get(2);
			float endX=dimensionsObj.get(3);
			float endY=dimensionsObj.get(4);
			
			//retreive information about the dimensions of the walls
			float height=data.get(2).get(0);
			ArrayList<Float> width=data.get(3);
			ArrayList<Float>  depthL=data.get(4);
			ArrayList<Float>  depthR=data.get(5);

			//retreive information about the distances captures by the autonomous robot's sensors
			ArrayList<Float>  disSensors=data.get(6);

			if(startX!=-1) {
				//the target object has been detected
				this.targetFound=true;
				System.out.println("Target detected");
				return 1;
			}
			if(disSensors.get(2)!=0|| disSensors.get(3)!=0 || disSensors.get(1)!=0){
				//the autonomous robot is too close from the wall in front
				System.out.println("prox in front: the autonomous robot is so too close from an eventual  wall in front, -> move back");
				try {
					System.out.println(" prox in front robot s position"+robPosition.toString());

					x0=pixToCmVert(robPosition.x-100)+distMar;
					y0=pixToCmHorz(robPosition.y)+distMar;
					x1=pixToCmVert(robPosition.x+100)+distMar;
					y1=pixToCmHorz(robPosition.y)+distMar;
					System.out.println("prox in front x0 y0 x1 y1"+x0+" "+ y0+" "+ x1+ " " +y1);
					lenSeg=(float) Math.sqrt(Math.pow((x0-x1),2)+Math.pow((y0-y1),2));
					cornerLeft=new Point(x0,y0);
					cornerRight=new Point(x1,y1);
					this.env.addWall(this.cpWall,cornerLeft,cornerRight,height,lenSeg);
					this.cpWall++;
					System.out.println("New Wall: "+cornerLeft.toString()+" "+cornerRight.toString());
					this.robot.rotate(90);
					this.robot.rotate(90);
					this.robot.move(distanceRob);
					Thread.sleep(27000);
					this.robot.rotate(-90);
					this.robot.rotate(-90);

				} catch (IOException e) {
					e.printStackTrace();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				return 0;
			}
			if(disSensors.get(0)!=0 ){
				//the autonomous robot is too close from the wall in front
				System.out.println("prox left: the autonomous robot is so too close from an eventual  wall in front, -> move back");
				try {

					x0=pixToCmVert(width.get(1))+distMar;
					y0=pixToCmHorz(width.get(2))+distMar;
					x1=pixToCmVert(width.get(3))+distMar;
					y1=pixToCmHorz(width.get(4))+distMar;
					System.out.println(" prox right "+x0+" "+ y0+" "+ x1+ " " +y1);
					x0=(float) (x0*((float)Math.cos(robRotation))-y0*Math.sin(robRotation)+robPosition.x);
					y0=(float) (y0*((float)Math.cos(robRotation))+x0*Math.sin(robRotation)+robPosition.y);
					x1=(float) (x1*((float)Math.cos(robRotation))-y1*Math.sin(robRotation)+robPosition.x);
					y1=(float) (y1*((float)Math.cos(robRotation))+x1*Math.sin(robRotation)+robPosition.y);
					System.out.println("prox right x0 y0 x1 y1"+x0+" "+ y0+" "+ x1+ " " +y1);
					lenSeg=(float) Math.sqrt(Math.pow((x0-x1),2)+Math.pow((y0-y1),2));
					cornerLeft=new Point(x0,y0);
					cornerRight=new Point(x1,y1);
					this.env.addWall(this.cpWall,cornerLeft,cornerRight,height,lenSeg);
					this.cpWall++;
					System.out.println("New Wall: "+cornerLeft.toString()+" "+cornerRight.toString());
					
					this.robot.rotate(90);
					this.robot.move(distanceRob);
					this.robot.rotate(-90);					
				} catch (IOException e) {
					e.printStackTrace();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				return 0;
			}

			if(disSensors.get(4)!=0 ){
				//the autonomous robot is too close from the wall in front
				System.out.println("prox right: the autonomous robot is so too close from an eventual  wall in front, -> move back");
				try {

					x0=pixToCmVert(width.get(1))+distMar;
					y0=pixToCmHorz(width.get(2))+distMar;
					x1=pixToCmVert(width.get(3))+distMar;
					y1=pixToCmHorz(width.get(4))+distMar;
					System.out.println(" prox right "+x0+" "+ y0+" "+ x1+ " " +y1);
					x0=(float) (x0*((float)Math.cos(robRotation))-x0*Math.sin(robRotation)+robPosition.x);
					y0=(float) (y0*((float)Math.cos(robRotation))+y0*Math.sin(robRotation)+robPosition.y);
					x1=(float) (x1*((float)Math.cos(robRotation))-y1*Math.sin(robRotation)+robPosition.x);
					y1=(float) (y1*((float)Math.cos(robRotation))+x1*Math.sin(robRotation)+robPosition.y);
					System.out.println("prox right x0 y0 x1 y1"+x0+" "+ y0+" "+ x1+ " " +y1);
					lenSeg=(float) Math.sqrt(Math.pow((x0-x1),2)+Math.pow((y0-y1),2));
					cornerLeft=new Point(x0,y0);
					cornerRight=new Point(x1,y1);
					this.env.addWall(this.cpWall,cornerLeft,cornerRight,height,lenSeg);
					this.cpWall++;
					System.out.println("New Wall: "+cornerLeft.toString()+" "+cornerRight.toString());
					
					this.robot.rotate(-90);
					this.robot.move(distanceRob);
					this.robot.rotate(90);
					this.robot.move(distanceRob);
					this.robot.rotate(90);
					
				} catch (IOException e) {
					e.printStackTrace();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				return 0;
			}
			
			if(disSensors.get(5)!=0 || disSensors.get(6)!=0 ){
					//the autonomous robot is too close from the wall in front
					System.out.println("rear prox: the autonomous robot is so too close from a wall behind it, -> exploration of this wall");
					try {
						this.robot.move(distanceRob);
						this.robot.rotate(90);
						this.robot.rotate(90);
					} catch (IOException | InterruptedException e) {
						e.printStackTrace();
					}
					return 0;
			}
				
			
			//building detected walls
			if(width.get(0)!=-1 && width.get(2)>threshClose){
					x0=pixToCmVert(width.get(1));
					y0=pixToCmHorz(width.get(2));
					x1=pixToCmVert(width.get(3));
					y1=pixToCmHorz(width.get(4));
					System.out.println("100 "+x0+" "+ y0+" "+ x1+ " " +y1);
					x0=(float) (x0*((float)Math.cos(robRotation))-width.get(2)*Math.sin(robRotation)+robPosition.x);
					y0=(float) (y0*((float)Math.cos(robRotation))+width.get(1)*Math.sin(robRotation)+robPosition.y);
					x1=(float) (x1*((float)Math.cos(robRotation))-width.get(4)*Math.sin(robRotation)+robPosition.x);
					y1=(float) (y1*((float)Math.cos(robRotation))+width.get(3)*Math.sin(robRotation)+robPosition.y);
					System.out.println("100 x0 y0 x1 y1"+x0+" "+ y0+" "+ x1+ " " +y1);
					lenSeg=(float) Math.sqrt(Math.pow((x0-x1),2)+Math.pow((y0-y1),2));
					cornerLeft=new Point(x0,y0);
					cornerRight=new Point(x1,y1);
					this.env.addWall(this.cpWall,cornerLeft,cornerRight,height,lenSeg);
					this.cpWall++;
					
			    }
			if(depthR.get(0)!=-1) {
				x0=pixToCmVert(depthR.get(1))+distMar;
				y0=pixToCmHorz(depthR.get(2))+distMar;
				x1=pixToCmVert(depthR.get(3))+distMar;
				y1=pixToCmHorz(depthR.get(4))+distMar;
				System.out.println(" 001 x0 y0 x1 y1"+x0+" "+ y0+" "+ x1+ " " +y1);
				x0=(float) (x0*(float)Math.cos(robRotation)-depthR.get(2)*Math.sin(robRotation)+robPosition.x);
				y0=(float) (y0*(float)Math.cos(robRotation)+depthR.get(1)*Math.sin(robRotation)+robPosition.y);
				x1=(float) (x1*(float)Math.cos(robRotation)-depthR.get(4)*Math.sin(robRotation)+robPosition.x);
				y1=(float) (y1*(float)Math.cos(robRotation)+depthR.get(3)*Math.sin(robRotation)+robPosition.y);
				System.out.println("001 x0 y0 x1 y1"+x0+" "+ y0+" "+ x1+ " " +y1);
				lenSeg=(float) Math.sqrt(Math.pow((x0-x1),2)+Math.pow((y0-y1),2));
				cornerLeft=new Point(x0,y0);
				cornerRight=new Point(x1,y1);
				this.env.addWall(this.cpWall,cornerLeft,cornerRight,height,lenSeg);
				this.cpWall++;
			}
			if(depthL.get(0)!=-1) {
				x0=pixToCmVert(depthL.get(1))+distMar;
				y0=pixToCmHorz(depthL.get(2))+distMar;
				x1=pixToCmVert(depthL.get(3))+distMar;
				y1=pixToCmHorz(depthL.get(4))+distMar;
				System.out.println("010 x0 y0 x1 y1"+x0+" "+ y0+" "+ x1+ " " +y1);
				x0=(float) (x0*(float)Math.cos(robRotation)-depthL.get(2)*Math.sin(robRotation)+robPosition.x);
				y0=(float) (y0*(float)Math.cos(robRotation)+depthL.get(1)*Math.sin(robRotation)+robPosition.y);
				x1=(float) (x1*(float)Math.cos(robRotation)-depthL.get(4)*Math.sin(robRotation)+robPosition.x);
				y1=(float) (y1*(float)Math.cos(robRotation)+depthL.get(3)*Math.sin(robRotation)+robPosition.y);
				System.out.println("010 x0 y0 x1 y1"+x0+" "+ y0+" "+ x1+ " " +y1);
				lenSeg=(float) Math.sqrt(Math.pow((x0-x1),2)+Math.pow((y0-y1),2));
				cornerLeft=new Point(x0,y0);
				cornerRight=new Point(x1,y1);
				this.env.addWall(this.cpWall,cornerLeft,cornerRight,height,lenSeg);
				this.cpWall++;
			}
			
			//the autonomous robot detect a segment (Horizontal) only
			if(width.get(0)!=-1) { 
				if(depthL.get(0)==-1 ) {
						if(depthR.get(0)==-1) {
							System.out.println("100");
							if(width.get(2)>threshClose){
								System.out.println("Close");
								try {
									this.robot.rotate(90);
									this.robot.move(distanceRob);
									System.out.println("before sleep");
									Thread.sleep(18000);
									System.out.println("after sleep");
									data=robot.captureData();
									disSensors=data.get(6);
									if(disSensors.get(2)!=0|| disSensors.get(3)!=0 || disSensors.get(1)!=0){
										System.out.println("next wall aprox");
										return 0;
									}
									this.robot.rotate(-90);
									return 0;
								} catch (IOException | InterruptedException e) {
									e.printStackTrace();
								}
							}
							else {
								System.out.println("Far");
								try {
									this.robot.move(distanceRob);
           							return 0;
								} catch (IOException | InterruptedException e) {
									e.printStackTrace();
								}
							}
						}
						else {
							System.out.println("101");
							if (width.get(2)>threshClose){
								System.out.println("Close");
								try {
									this.robot.rotate(90);
									this.robot.rotate(90);
									this.robot.move(this.distanceRob);
									System.out.println("before sleep");
									Thread.sleep(27000);
									System.out.println("after sleep");
									data=robot.captureData();
									disSensors=data.get(6);
									if(disSensors.get(2)!=0|| disSensors.get(3)!=0 || disSensors.get(1)!=0){
										System.out.println("next wall aprox");
										return 0;
									}
									this.robot.rotate(-90);}
								 catch (IOException | InterruptedException e) {
									e.printStackTrace();
								}
								return 0;
							}
							else {
								System.out.println("Far");
								try {
									this.robot.move(distanceRob);
								} catch (IOException | InterruptedException e) {
									e.printStackTrace();
								}
								return 0;
							}
						}
			    }
				else {
					if(depthR.get(0)==-1) {
						System.out.println("110");
						if(width.get(2)>threshClose){
							System.out.println("Close");
							try {
								this.robot.rotate(90);
								this.robot.move(distanceRob);
								System.out.println("before sleep");
								Thread.sleep(27000);
								System.out.println("after sleep");
								data=robot.captureData();
								disSensors=data.get(6);
								if(disSensors.get(2)!=0|| disSensors.get(3)!=0 || disSensors.get(1)!=0){
									System.out.println("next wall aprox");
									return 0;
								}
								this.robot.rotate(-90);
							} catch (IOException | InterruptedException e) {
									e.printStackTrace();
								}
							return 0;
						}
						else {
							System.out.println("Far");
							try {
								this.robot.move(distanceRob);
							} catch (IOException | InterruptedException e) {
								e.printStackTrace();
							}
						}
					}
					else {
						System.out.println("111");
						if (width.get(2)>threshClose){
							System.out.println("Close");
							try {
								this.robot.rotate(90);
							} catch (IOException | InterruptedException e1) {
								e1.printStackTrace();
							}
							return 0;
						}
						else {
							System.out.println("Far");
							try {
								this.robot.move(distanceRob);
							} catch (IOException | InterruptedException e) {
								e.printStackTrace();
							}
						return 0;
						}
					}
					
					
				}
			}
			else {
				if(depthL.get(0)==-1 ) {
					if(depthR.get(0)==-1) {
						System.out.println("000");
							try {
								this.robot.rotate(90);
								this.robot.rotate(90);
								this.robot.move(distanceRob);
								Thread.sleep(27000);
								this.robot.rotate(-90);
								this.robot.rotate(-90);
							} catch (IOException | InterruptedException e) {
								e.printStackTrace();
							}
						return 0;

					}
					else {
						System.out.println("001");
						try {
							this.robot.rotate(90);
						} catch (IOException | InterruptedException e) {
							e.printStackTrace();
						}
					return 0;

					}
				}
				else {
					if(depthR.get(0)==-1) {
						System.out.println("010");
						try {
							this.robot.rotate(-90);
						} catch (IOException | InterruptedException e) {
							e.printStackTrace();
						}
					return 0;
					}
					else {
						System.out.println("011");
						try {
							this.robot.rotate(90);
						} catch (IOException | InterruptedException e) {
							e.printStackTrace();
						}
					return 0;
						
					}
				}
			}
		return 0;
	}
		

	//"dataBase" is a file containing the name of the image and its real dimensions, this function allows to store the DB
	public void readDBObjects() {
	
		try (FileReader reader = new FileReader(this.localURL+"/ressources/objects/dataBase");
	           BufferedReader br = new BufferedReader(reader)) {
	           String line;
	           while ((line = br.readLine()) != null) {
	        	   String[] parts = line.split("/");
	        	   HashMap<String, String> qrProperties=new HashMap<String,String>();
				   qrProperties.put("id", ""+parts[0]);
				   qrProperties.put("img",parts[1]);
				   qrProperties.put("width", parts[2]);
				   qrProperties.put("height",parts[3]);
				   this.dbObjects.add(qrProperties);               
	           }
	        } catch (IOException e) {
            System.err.format("IOException: %s%n", e);
        }
		
	}
	public float pixToCmVert(float x) {
		return (float) (1.710317173*Math.pow(10,-4)*Math.pow(x,3)-1.499946521*Math.pow(10,-2)*Math.pow(x,2)+6.987860838*Math.pow(10,-1)*x+ 1.507260906 );
	}
	public float pixToCmHorz(float x) {
		//:  y = 5.037695062·10-6 x12 + 1.297822371·10-3 x1 x2 - 4.471868406·10-4 x22 + 6.672979764·10-2 x1 + 8.811965407·10-2 x2 + 6.158770991·10-3
		return (float) (8.163781696*Math.pow(10,-6) *Math.pow(x,3)-3.456492302*Math.pow(10,-3)*Math.pow(x,2)+4.371800643*Math.pow(10,-1)*x + 2.221220181);
	}
}
