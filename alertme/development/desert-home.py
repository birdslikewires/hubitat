#! /usr/bin/python
'''
Hacking into the iris door sensor
Have fun
'''
from xbee import ZigBee 
import datetime
import time
import serial
import sys, traceback
import shlex
import Queue
from struct import *
import binascii
import inspect


# line number for debugging
def getLineNumber():
    return inspect.stack()[1][2]
    
# show data formatted so I can read it
def showData(data):
    print "********** Message Contents"
    for key, value in data.iteritems():
        if key == "id":
            print key, value
        else:
            print key, "".join("%02x " % ord(b) for b in data[key])
    print "**********"
    
def showClusterData(lAddr,sAddr, clusterId, data):
    print int(time.time()),
    print "".join("%02x" % ord(b) for b in lAddr) + \
        " " + \
        "".join("%02x" % ord(b) for b in sAddr) + \
        " clid "+"%04x" % clusterId + "-" + \
        "".join("%02x " % ord(b) for b in data)

# this is a call back function for XBee receive. 
# When a message comes in this function will 
# get the data.
# I had to use a queue to make sure there was enough time to 
# decode the incoming messages. Otherwise, in heavy traffic
# periods, I'd get a new message while I was still working on 
# the last one.
def messageReceived(data):
    #print "queueing message"
    messageQueue.put(data)

def handleMessage(data):
    try:
#        if data['source_addr_long'] not in \
#            ['\x00\x0d\x6f\x00\x04\x51\x07\x82',]:
#            return
        #print 'gotta packet' 
        #showData(data)
        if (data['id'] == 'rx_explicit'):
            #print "RX Explicit"
            #showData(data)
            clusterId = (ord(data['cluster'][0])*256) + ord(data['cluster'][1])
            #print 'Cluster ID:', hex(clusterId),

            if (data['profile']=='\x00\x00'): # The General Profile
                print 'Cluster ID:', hex(clusterId),
                print "profile id:", repr(data['profile'])
                if (clusterId == 0x0000):
                    print ("Network (16-bit) Address Request")
                    #showData(data)
                elif (clusterId == 0x0004):
                    # Simple Descriptor Request, 
                    print("Simple Descriptor Request")
                    #showData(data)
                elif (clusterId == 0x0005):
                    # Active Endpoint Request, 
                    print("Active Endpoint Request")
                    #showData(data)
                elif (clusterId == 0x0006):
                    print "Match Descriptor Request"
                    '''
                    the switch looks for clusters under profile
                    c216, and I respond with only 1 cluster 02
                    '''
                    showData(data)
                    time.sleep(2)
                    print "Sending match descriptor response"
                    zb.send('tx_explicit',
                        dest_addr_long = data['source_addr_long'],
                        dest_addr = data['source_addr'],
                        src_endpoint = '\x00',
                        dest_endpoint = '\x00',
                        cluster = '\x80\x06',
                        profile = '\x00\x00',
                        options = '\x01',
                        data = data['rf_data'][0:1] + '\x00\x00\x00\x01\x02'
                    )
                    # The contact switch is a bit slow, give it 
                    # some time to digest the messages.
                    time.sleep(2)
                    zb.send('tx_explicit',
                        dest_addr_long = data['source_addr_long'],
                        dest_addr = data['source_addr'],
                        src_endpoint = '\x02',
                        dest_endpoint = '\x02',
                        cluster = '\x00\xf6',
                        profile = '\xc2\x16',
                        data = '\x11\x01\xfc'
                        )
                    time.sleep(2)
                elif (clusterId == 0x0008):
                    # I couldn't find a definition for this 
                    print("This was probably sent to the wrong profile")
                elif (clusterId == 0x13):
                    # This is the device announce message.
                    print 'Device Announce Message'
                    # this will tell me the address of the new thing
                    # so I'm going to send an active endpoint request
                    print 'Sending active endpoint request'
                    epc = '\xaa'+data['source_addr'][1]+data['source_addr'][0]
                    print "".join("%02x " % ord(b) for b in epc)
                    zb.send('tx_explicit',
                        dest_addr_long = data['source_addr_long'],
                        dest_addr = data['source_addr'],
                        src_endpoint = '\x00',
                        dest_endpoint = '\x00',
                        cluster = '\x00\x05',
                        profile = '\x00\x00',
                        options = '\x01',
                        data = epc
                    )
                elif (clusterId == 0x8000):
                    print("Network (16-bit) Address Response")
                    #showData(data)
                elif (clusterId == 0x8005):
                    # this is the Active Endpoint Response This message tells you
                    # what the device can do, but it isn't constructed correctly to match 
                    # what the switch can do according to the spec.  This is another 
                    # message that gets it's response after I receive the Match Descriptor
                    print 'Active Endpoint Response'
                # elif (clusterId == 0x0006):
                elif (clusterId == 0x8038):
                    print("Management Network Update Request");
                else:
                    print ("Unimplemented Cluster ID", hex(clusterId))
                    print
            elif (data['profile']=='\xc2\x16'): # Alertme Specific
                if (clusterId == 0xee):
                    clusterCmd = ord(data['rf_data'][2])
                    status = ''
                    if (clusterCmd == 0x80):
                        if (ord(data['rf_data'][3]) & 0x01):
                            status = "ON"
                        else:
                            status = "OFF"
                elif (clusterId == 0xef):
                    clusterCmd = ord(data['rf_data'][2])
                    status = data['rf_data'] # cut down on typing
                    if (clusterCmd == 0x81):
                        usage = unpack('<H', status[3:5])[0]
                    elif (clusterCmd == 0x82):
                        usage = unpack('<L', status[3:7])[0] / 3600
                        upTime = unpack('<L', status[7:11])[0]
                        #print ("%s Minute Stats: Usage, %d Watt Hours; Uptime, %d Seconds" %(name, usage/3600, upTime))
                elif (clusterId == 0xf0):
                    showClusterData(data['source_addr_long'],data['source_addr'],clusterId,data['rf_data'])
                    # If the cluster cmd byte is 'xfb', it's a status
                    if data['rf_data'][2] == '\xfb':
                        status = data['rf_data'] # just to make typing easier
                        if status[3] == '\x1f':
                            print " Door Sensor",
                            print str(float(unpack("<h", status[8:10])[0])\
                                / 100.0 * 1.8 + 32) + "F",
                            if ord(status[-1]) & 0x01 == 1:
                                print "reed switch open",
                            else:
                                print "reed switch closed",
                            if ord(status[-1]) & 0x02 == 0:
                                print "tamper switch open",
                            else:
                                print "tamper switch closed",
                            
                        elif status[3] == '\x1c':
                            #  Never found anything useful in this
                            print "Power Switch",
                        elif status[3] == '\x1d':
                            print " Key Fob",
                            print str(float(unpack("<h", status[8:10])[0])\
                                / 100.0 * 1.8 + 32) + "F",
                            unpack('<I',status[4:8])[0]
                            print 'Counter', unpack('<I',status[4:8])[0],
                        elif status[3] == '\x1e':
                            # This indicates a door sensor
                            # with an invalid temperature reading
                            # the other items are OK 
                            print " Door Sensor",
                            print "Temperature invalid",
                            if ord(status[-1]) & 0x01 == 1:
                                print "reed switch open",
                            else:
                                print "reed switch closed",
                            if ord(status[-1]) & 0x02 == 0:
                                print "tamper switch open",
                            else:
                                print "tamper switch closed",
                            #This may be the missing link to this thing
                            print 'sending missing link',
                            zb.send('tx_explicit',
                               dest_addr_long = data['source_addr_long'],
                               dest_addr = data['source_addr'],
                               src_endpoint = data['dest_endpoint'],
                               dest_endpoint = data['source_endpoint'],
                               cluster = '\x00\xf0',
                               profile = '\xc2\x16',
                               data = '\x11\x39\xfd'
                            )
                            pass
                        else:
                            print " Don't know this device yet",
                        print ''
                    else:
                        print " Unknow cluster command"
                        print ''
                    pass
                elif (clusterId == 0x00f2):
                    showClusterData(data['source_addr_long'],data['source_addr'],clusterId,data['rf_data'])
                    print 'Tamper Switch Changed State to',
                    status = data['rf_data'] 
                    if ord(status[3]) == 0x02:
                        print "Open",
                    else:
                        print "Closed",
                    print ''
                    pass
                elif (clusterId == 0x00f3):
                    showClusterData(data['source_addr_long'],data['source_addr'],clusterId,data['rf_data'])
                    print ' Key Fob Button',
                    status = data['rf_data'] 
                    print ord(status[3]),
                    if status[2] == '\x01':
                        print 'Closed',
                    elif status[2] == '\x00':
                        print 'Open',
                    else:
                        print 'Unknown',
                    print 'Counter', unpack('<H',status[5:7])[0],
                    print ''
                    pass
                elif (clusterId == 0xf6):
                    showClusterData(data['source_addr_long'],data['source_addr'],clusterId,data['rf_data'])
                    print ''
                    print "Identify Message"
                    #extract vendor strings
                    v = data['rf_data']
                    vendorstr = " - Vendor:"
                    start = 21
                    datalen=len(v)
                    while(start < datalen):
                        slen=ord(v[start])
                        vendorstr = vendorstr + " " + v[start+1:start+1+slen]
                        start = start+slen+1
                    print vendorstr
                    print "Sending init message"
                    zb.send('tx_explicit',
                       dest_addr_long = data['source_addr_long'],
                       dest_addr = data['source_addr'],
                       src_endpoint = '\x00',
                       dest_endpoint = '\x02',
                       cluster = '\x00\xf0',
                       profile = '\xc2\x16',
                       data = '\x19\x41\xfa\x00\x01'
                    )
                elif (clusterId == 0x0500): # This is the security cluster
                    showClusterData(data['source_addr_long'],data['source_addr'],clusterId,data['rf_data'])
                    showData(data)
                    # When the switch first connects, it come up in a state that needs
                    # initialization, this command seems to take care of that.
                    # So, look at the value of the data and send the command.
                    if data['rf_data'][3:7] == '\x15\x00\x39\x10':
                        print "sending initialization"
                        zb.send('tx_explicit',
                            dest_addr_long = data['source_addr_long'],
                            dest_addr = data['source_addr'],
                            src_endpoint = data['dest_endpoint'],
                            dest_endpoint = data['source_endpoint'],
                            cluster = '\x05\x00',
                            profile = '\xc2\x16',
                            data = '\x11\x80\x00\x00\x05'
                        )
                    # The switch state is in byte [3] and is a bitfield
                    # bit 0 is the magnetic reed switch state
                    # bit 3 is the tamper switch state
                    switchState = ord(data['rf_data'][3])
                    if switchState & 0x04:
                        print 'Tamper Switch Closed',
                    else:
                        print 'Tamper Switch Open',
                    if switchState & 0x01:
                        print 'Reed Switch Opened',
                    else:
                        print 'Reed Switch Closed',
                    print ''
                    pass
                else:
                    print ("Unimplemented Cluster ID", hex(clusterId))
                    print
            else:
                print ("Unimplemented Profile ID")
        elif(data['id'] == 'route_record_indicator'):
            print("Route Record Indicator")
        else:
            print("some other type of packet")
            print(data)
    except:
        print "I didn't expect this error:", sys.exc_info()[0]
        traceback.print_exc()
       
def stopXBee():
    print("XBee stop handler")
    zb.halt()
    ser.close()

####################### Actually Starts Here ################################    
#------------ XBee Stuff -------------------------
# this is the /dev/serial/by-id device for the USB card that holds the XBee
ZIGBEEPORT = "/dev/serial/by-id/usb-FTDI_FT232R_USB_UART_A901QL3F-if00-port0"
ZIGBEEBAUD_RATE = 57600
# Open serial port for use by the XBee
ser = serial.Serial(ZIGBEEPORT, ZIGBEEBAUD_RATE)
# The XBee addresses I'm dealing with
BROADCAST = '\x00\x00\x00\x00\x00\x00\xff\xff'
UNKNOWN = '\xff\xfe' # This is the 'I don't know' 16 bit address

# create a queue to put the messages into so they can
# be handled in turn without one interrupting the next.
messageQueue = Queue.Queue(0)

# Create XBee library API object, which spawns a new thread
zb = ZigBee(ser, callback=messageReceived)
print "started"
while True:
    try:
        if messageQueue.qsize() > 0:
            #print "getting message"
            message = messageQueue.get()
            handleMessage(message)
            messageQueue.task_done();
            sys.stdout.flush() # if you're running non interactive, do this
    except KeyboardInterrupt:
        print "Keyboard interrupt"
        zb.halt()
        ser.close()
        break
    except:
        print "Unexpected error:", sys.exc_info()[0] 
        traceback.print_exc()
        break

print ("After the while")
# just in case
zb.halt()
ser.close()
