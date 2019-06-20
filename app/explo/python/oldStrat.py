'''
    File name: utils.py
    Author: Asma BRAZI
    Author: Alexandre HEINTZMANN
    Date created: 13/04/2019
    Date last modified: 22/04/2019
    Python Version: 3.6.7
'''
from __future__ import print_function
from picamera import PiCamera
from time import sleep
from shapely.geometry import LineString
from shapely.geometry.collection import GeometryCollection
from shapely.geometry.point import Point
import os.path
import os
import datetime
from PIL import Image
import numpy as np
import cv2
from imageUtil import *
from matplotlib import pyplot as plt
import sys
import json
import math
from InputView import *
from threading import Thread
from picamera.array import PiRGBArray
from picamera import PiCamera
import time
import sched
import collections


import itertools
import random
from itertools import starmap

def takePicture(r1=600,r2=400):
    urlDirectory="/home/pi/VisualNav"
    imgName=datetime.datetime.now().strftime('%H%M%S%d%m')
    urlImg=urlDirectory+'/'+imgName+'.jpg'
    camera = PiCamera()
    camera.resolution = (r1,r2)
    camera.start_preview()
    sleep(2)
    camera.capture(urlImg)
    camera.stop_preview()
    return urlImg
# the function HoughLinesP return the maximum vertical line detected (let it be our height of the wall)
#maxLG:  the height of the wall that we allow to have
def HoughLinesP(im='v.jpg',maxLG=350):
    img = cv2.imread(im)
    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    edges = cv2.Canny(gray, 75, 150)
    lines = cv2.HoughLinesP(edges, 1, 1, 30, maxLineGap=maxLG)
    tmpMaxLine=[]
    for line in lines:
        x1, y1, x2, y2 = line[0]
        tmpMaxLine.append(abs(y1-y2))
    tmpMaxLine=np.array(tmpMaxLine)
    indexMaxElement=np.where(tmpMaxLine == np.amax(tmpMaxLine))[0][0]
    x1, y1, x2, y2 = lines[indexMaxElement][0]
    cv2.line(img, (x1, y1), (x2, y2), (0, 255, 0), 3)
    #cv2.imshow("Edges", edges)
    #cv2.imshow("Image", img)
    #cv2.waitKey(0)
    #cv2.destroyAllWindows()
    return abs(y1-y2)

#create a responsible thread of a CMD executed by the Thymio
class AsebaThread(Thread):
    def __init__(self,cmd):
        Thread.__init__(self)
        self.cmd=cmd
    def run(self):
        os.system(self.cmd)

#taking picture by the rhe robot


#type is the name of the picture 0:QRCODE 1:PEPPER 2:PLATON
def getThreshold(type):
    if(type==0):
        return 12000000.0
    if(type==1):
        return 30000000.0
    if(type==2):
        return 30000000.0

#multi= u: unique object to detect; m: multi, because for corners we assume that there is only PEPPER on.
#So we dont really need to check each element on the DB
#nbRefs: number of objects in the DB
#objects must have their name in the range of (0,nbRefs)

def recognition(img,ref="0",multi='u',nbRefs=3):
    results=[]
    if (multi=='m'):
        references=np.arange(0,nbRefs,1).tolist()
    else:
        references=[int(ref)] 

    for t in references:
        threshold=getThreshold(t)
        reference="DB/"+str(t)+".png"
        urlDirectory="/home/pi/VisualNav"
        match=False
        imgURL = img

        # load the image image, convert it to grayscale, and detect edges
        template = cv2.imread(reference,0)
        # make the template smaller to have better performances
        template = resize(template, int(template.shape[1] * 0.25))
        template = cv2.Canny(template, 50, 150)
        (tH, tW) = template.shape[:2]
        #cv2.imshow("Template", template)

        # load the image, convert it to grayscale,
        image = cv2.imread(imgURL,0)
        image = normalise(image)
        # initialize the variable to keep track of the matched region
        found = None
        # loop over the scales of the image
        # might take a long time to execute if the 2nd argument of linespace is to high
        for scale in np.linspace(0.1, 2, 20)[::-1]:
            #print(yes)
            # resize the image according to the scale, and keep track of the ratio of the resizing
            resized = resize(image, int(image.shape[1] * scale))
            r = image.shape[1] / float(resized.shape[1])

            # if the resized image is smaller than the template, then break from the loop
            if resized.shape[0] < tH or resized.shape[1] < tW:
                break

            # detect edges in the resized, grayscale image and apply template
            # matching to find the template in the image
            edged = cv2.Canny(resized, 50, 150)
            result = cv2.matchTemplate(edged, template, cv2.TM_CCOEFF)
            (minVal, maxVal, _, maxLoc) = cv2.minMaxLoc(result)

            # draw a bounding box around the detected region
            clone = np.dstack([edged, edged, edged])
            cv2.rectangle(clone, (maxLoc[0], maxLoc[1]),
                (maxLoc[0] + tW, maxLoc[1] + tH), (0, 0, 255), 2)
            #cv2.imshow("Visualize", clone)
            #cv2.waitKey(0)

            # if we have found a new maximum correlation value, then update the variable
            if found is None or maxVal > found[0]:
                found = (maxVal, maxLoc, r)
                #print(found)
                if(maxVal>=threshold):
                    match=True
          
        # compute the (x, y) coordinates of the bounding box based on the resized ratio
        (_, maxLoc, r) = found
        (startX, startY) = (int(maxLoc[0] * r), int(maxLoc[1] * r))
        (endX, endY) = (int((maxLoc[0] + tW) * r), int((maxLoc[1] + tH) * r))

        # draw a bounding box around the detected result and display the image
        #cv2.rectangle(image, (startX, startY), (endX, endY), (0, 0, 255), 2)
        #cv2.imshow("Image", image)
        #cv2.waitKey(0)
        imgWidth=(np.shape(image)[1]/int(tW * r) *11.5)
        # camera wide angle by default
        angle = 54
        # trigonometry to get the distance from the detected object
        dist = (imgWidth/2)/math.tan(math.radians(angle/2))
        if not match:
            results.append([-1,[-1,-1,-1,-1]])
        else:
            results.append([round(dist,2),[startX, startY, endX, endY]])
    return results 

#according to the angle and the fName, we order the robot to rotate or move
def getAsebaFileD(fName,angle):
    d=angle
    speed=1      
    fName+="D"

    with open(fName+".aesl","r+") as f:
        print(f)

        fl=f.readlines()
        f.seek(0)
        for i in fl:
            if "var distance" in i:
                print(i+"\n")
                print(str(int(d))+"\n")
                f.write("var distance="+str(int(d))+"\n")
            else:
                if "var reverse=" not in i:
                    f.write(i)
                else:
                    f.write("var reverse="+str(int(speed))+"\n")
        f.truncate()
def intersection(l1,l2):
    line1 = LineString([(l1[0],l1[1]), (l1[2],l1[3])])
    line2 = LineString([(l2[0],l2[1]), (l2[2],l2[3])])
    result=line1.intersection(line2) # the type of the result: shapely.geometry.point.Point
    if isinstance(result, GeometryCollection):
        return False
    if isinstance(result, LineString):
        return result.coords[0]
    if isinstance(result, Point):
        return result.x,result.y
def newCorner(r,set,borders):
    for b in borders:
        if abs(r[0]-b)<5 or abs(r[1]-b)<5 or r[1]<borders[2]/3:
            return False 
    for t in set:
        if abs(t[0]-r[0]<10) and abs(t[1]-r[1]<10) and r[1]>borders[2]/3:
            return False
    return True

######################################
# First method for corner detection  #
######################################
def goodFeatureCorner(im):
    img = cv2.imread(im)
    # convert image to gray scale image 
    gray = cv2.cvtColor(img,cv2.COLOR_BGR2GRAY)

    # detect corners with the goodFeaturesToTrack function. 
    corners = cv2.goodFeaturesToTrack(gray, 10, 0.01, 500) 
    corners = np.int0(corners) 
  
    # we iterate through each corner,  
    # making a circle at each point that we think is a corner. 
    for i in corners: 
        x, y = i.ravel() 
        print(x,y)
        cv2.circle(img, (x, y), 3, 255, -1) 
  
    cv2.imshow("Image", img)
    cv2.waitKey(0)
    cv2.destroyAllWindows()
    #return corners
def StreamGFCornerDetection():
    camera=PiCamera()
    camera.resolution=(600,400)
    camera.framerate=32
    rawCapture=PiRGBArray(camera,size=(600,400))
    time.sleep(0.1)
    for frame in camera.capture_continuous(rawCapture, format="bgr", use_video_port=True):
        # grab the raw NumPy array representing the image, then initialize the timestamp
        # and occupied/unoccupied text
        image = frame.array
        goodFeatureCorner(image)
        # show the frame
        cv2.imshow("Frame", image)

        key = cv2.waitKey(1) & 0xFF
 
        # clear the stream in preparation for the next frame
        rawCapture.truncate(0)
 
        # if the `q` key was pressed, break from the loop
        if key == ord("q"):
            break
def PictureGFCornerDetection():
    goodFeatureCorner(takePicture())

######################################
# Second method for corner detection #
######################################
def HoughLinesCorner(im):
    dic={}
    img =  cv2.imread(im)
    n,m,_=np.array(img).shape
    borders=[0.0,n,m]
    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    edges = cv2.Canny(gray, 75, 150)
    minLG=30
    maxLG = 100
    lines = cv2.HoughLinesP(edges, 1, np.pi/180, threshold=60,maxLineGap=maxLG)
    #sort lines by length
    sortedLineLength=[]
    for line in lines:
        x1, y1, x2, y2 = line[0]
        dic[-np.sqrt((x1-x2)**2+(y1-y2)**2)]=line[0]
        sortedLineLength.append(np.array([line[0],-np.sqrt((x1-x2)**2+(y1-y2)**2)]))
        cv2.line(img, (x1, y1), (x2, y2), (0, 255, 0), 1)
    dic= collections.OrderedDict(sorted(dic.items()))
    sortedDic={}
    for k, v in dic.items():
        sortedDic[-k]=v
    cornersFiltred=[]
    intersections=[]

    #calculate the intersections
    for k1,l1 in sortedDic.items():
        for k2,l2 in sortedDic.items():
            r=intersection(l1,l2)
            if r:
                bo=True
                x,y=r
                if len(cornersFiltred)==0:
                    for b in borders:
                        if abs(r[0]-b)<5 or abs(r[1]-b)<5:
                            bo=False
                    if bo:
                        cornersFiltred.append(r)
                        cv2.circle(img, (int(x), int(y)), 3, (0, 0, 255), -1)
                else:
                    if newCorner(r,cornersFiltred,borders):
                        cornersFiltred.append(r)
                        cv2.circle(img, (int(x), int(y)), 3, (0, 0, 255), -1)
    #cv2.imshow("Edges", edges)
    cv2.imshow("Image", img)
    cv2.waitKey(0)
    cv2.destroyAllWindows()
    #return cornersFiltred

def StreamHLCornerDetection():
    camera=PiCamera()
    camera.resolution=(600,400)
    camera.framerate=32
    rawCapture=PiRGBArray(camera,size=(600,400))
    time.sleep(0.1)
    for frame in camera.capture_continuous(rawCapture, format="bgr", use_video_port=True):
        # grab the raw NumPy array representing the image, then initialize the timestamp
        # and occupied/unoccupied text
        image = frame.array
        HoughLinesCorner(image)
        # show the frame
        cv2.imshow("Frame", image)

        key = cv2.waitKey(1) & 0xFF
 
        # clear the stream in preparation for the next frame
        rawCapture.truncate(0)
 
        # if the `q` key was pressed, break from the loop
        if key == ord("q"):
            break
def PictureHLCornerDetection():
    HoughLinesCorner(takePicture())

#Calculate the equation of the segment with two points p1 and p2
#return the coef a and  of the equation
def coefLine(p1,p2):
    if abs(p2[0]-p1[0])<0.001:
        a=0
        c=p1[0]
    else:
        a=(p2[1]-p1[1])/(p2[0]-p1[0])
        if p1[0]>0.001:
            c=p1[1]/(a*p1[0])
        if p2[0]>0.001:
            c=p2[1]/(a*p2[0])
    return a,1,c
#Calculate the shortest distance between a point and a line
def distPointLine(p1,p2,p3):
    p1=np.array(p1)
    p2=np.array(p2)
    p3=np.array(p3)
    return np.cross(p2-p1,p3-p1)/np.linalg.norm(p2-p1)

#Calculate the longest distance between two lines, by checking each extremity with the other line. 
def distLineLine(l1,l2):
    x1,y1,x2,y2=l1
    x3,y3,x4,y4=l2
    e11=[x1,y1]
    e12=[x2,y2]
    e21=[x3,y3]
    e22=[x4,y4]
    d1=distPointLine(e11,e12,e21)
    d2=distPointLine(e11,e12,e22)
    d3=distPointLine(e21,e22,e11)
    d4=distPointLine(e21,e22,e12)
    return max(d1,d2,d3,d4)

#Calculate the longest distance between two lines, by checking each extremity with the other line. 
def minDistLineLine(l1,l2,thresholdA=0.1,thresholdC=1):
    x1,y1,x2,y2=l1
    x3,y3,x4,y4=l2
    a1,b1,c1=coefLine([x1,y1],[x2,y2])
    a2,b2,c2=coefLine([x3,y3],[x4,y4])
    if abs(a1-a2)<thresholdA and abs(c1-c2)<thresholdC:
        return True
    return False
#Treat by a couple of lines from the entry 'lines', if they are too close depending on a threshold, they are merged
#Teh set of kept lines is returned
def mergeLines(lines,threshold=30):
    filtred_lines=[]
    lines_copy=lines.copy()
    dic={}
    #sort lines by length
    sortedLineLength=[]
    for line in lines:
        x1, y1, x2, y2 = line
        dic[-np.sqrt((x1-x2)**2+(y1-y2)**2)]=line
        sortedLineLength.append(np.array([line,-np.sqrt((x1-x2)**2+(y1-y2)**2)]))
    dic= collections.OrderedDict(sorted(dic.items()))
    sortedDic=[]
    for k, v in dic.items():
        sortedDic.append([-k,v]) #distance + line

    #convert liens arry to a dic, this facilitates to filter lines at deletion
    dicLines = { i : sortedDic[i] for i in range(0, len(sortedDic) ) }
    for Kl1 in list(dicLines):
        if Kl1 in dicLines.keys():
            Vl1=dicLines[Kl1]
            Kl_OK=False
            #print('Kl1,Vl1',Kl1,Vl1)
            for Kl2 in list(dicLines):
                if Kl2  in dicLines.keys():
                    #print("Kl2",Kl2)
                    Vl2=dicLines[Kl2]
                    if Kl1!=Kl2:
                        #print('Kl2,Vl2',Kl2,Vl2)
                        #print('distance',dist)
                        if(minDistLineLine(Vl1,Vl2)):
                            print('Merge lines',Kl1,Kl2)
                            x0,y0,x1,y1=Vl1
                            l1_length=np.sqrt((x0-x1)**2+(y0-y1)**2)
                            x0,y0,x1,y1=Vl2
                            l2_length=np.sqrt((x0-x1)**2+(y0-y1)**2)
                            if l1_length>l2_length:
                                filtred_lines.append(Vl1)
                            else:
                                filtred_lines.append(Vl1)
                            dicLines.pop(Kl1)
                            dicLines.pop(Kl2)
                            #print('Dic filt',dicLines.keys())
                            Kl_OK=True
                if Kl_OK:
                    break
    return filtred_lines

#Use of the LSD detection in order to capture the necessary segments after filtering
def LSDDetection(im):
    img = cv2.imread(im)
    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    gray= cv2.Canny(gray, 100, 150)
    lsd = cv2.createLineSegmentDetector(0)
    lines = lsd.detect(gray)[0]
    lines_vert=[]
    lines_non_vert=[]
    for line in lines:
        x0 = int(round(line[0][0]))
        y0 = int(round(line[0][1]))
        x1 = int(round(line[0][2]))
        y1 = int(round(line[0][3]))
        if np.sqrt((x0-x1)**2+(y0-y1)**2)>20:
            if abs(x0-x1)<20:
                #cv2.line(img, (x0, y0), (x1,y1), (0,255,0), 1, cv2.LINE_AA)
                lines_vert.append(line[0])
            else:
                lines_non_vert.append(line[0])
    #print("Vertical lines are:",lines_vert)
    #print("Non-Vertical lines are:",lines_non_vert)
   # print("lines_non_vert:",lines_non_vert)
    filtred_non_vert_lines=mergeLines(lines_non_vert)
    #print()
    #print('filtred_non_vert_lines: ',filtred_non_vert_lines)
    for l1 in filtred_non_vert_lines:
        cv2.line(img, (l1[0], l1[1]), (l1[2],l1[3]), (255,0,0), 1, cv2.LINE_AA)
    filtred_vert_lines=mergeLines(lines_vert)
    for l1 in filtred_vert_lines:
        cv2.line(img, (l1[0], l1[1]), (l1[2],l1[3]), (0,255,0), 1, cv2.LINE_AA)
    #drawn_img = lsd.drawSegments(img,lines)
    cv2.imshow("Image", img)
    #cv2.imshow("Edges", gray)
    cv2.waitKey(0)
    cv2.destroyAllWindows()

def PictureLSDDetection():
    LSDDetection(takePicture())
#######################################
#PictureGFCornerDetection()
PictureLSDDetection()