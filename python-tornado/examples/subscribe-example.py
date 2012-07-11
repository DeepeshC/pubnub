## www.pubnub.com - PubNub Real-time push service in the cloud. 
# coding=utf8

## PubNub Real-time Push APIs and Notifications Framework
## Copyright (c) 2010 Stephen Blum
## http://www.pubnub.com/

## -----------------------------------
## PubNub 3.1 Real-time Push Cloud API
## -----------------------------------

import sys
import tornado
sys.path.append('../')
from Pubnub import Pubnub

publish_key   = len(sys.argv) > 1 and sys.argv[1] or 'demo'
subscribe_key = len(sys.argv) > 2 and sys.argv[2] or 'demo'
secret_key    = len(sys.argv) > 3 and sys.argv[3] or 'demo'
cipher_key    = len(sys.argv) > 4 and sys.argv[4] or '' ##(Cipher key is Optional)
ssl_on        = len(sys.argv) > 5 and bool(sys.argv[5]) or False

## -----------------------------------------------------------------------
## Initiate Pubnub State
## -----------------------------------------------------------------------
pubnub = Pubnub( publish_key, subscribe_key, secret_key,cipher_key, ssl_on )
crazy  = 'hello_world'

## -----------------------------------------------------------------------
## Publish Example
## -----------------------------------------------------------------------
def publish_complete(info):
    print(info)

## Publish string
pubnub.publish({
    'channel' : crazy,
    'message' : 'Hello World!',
    'callback' : publish_complete
})

## Publish list
li = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday']
pubnub.publish({
    'channel' : crazy,
    'message' : li,
    'callback' : publish_complete
})

## Publish Dictionary Object
pubnub.publish({
    'channel' : crazy,
    'message' : { 'some_key' : 'some_val' },
    'callback' : publish_complete
})

## -----------------------------------------------------------------------
## IO Event Loop
## -----------------------------------------------------------------------
tornado.ioloop.IOLoop.instance().start()
