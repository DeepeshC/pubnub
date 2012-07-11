--
-- PubNub 3.1 : UUID Example
--

require "pubnub"
require "crypto"

--
-- INITIALIZE PUBNUB STATE
--
pubnub_obj = pubnub.new({
    publish_key   = "",
    subscribe_key = "",
    secret_key    = "",
    ssl           = nil,
    origin        = ""
})

-- 
-- TEXT OUT - Quick Print
-- 
local textoutline = 1
local function textout( text )
    
    if textoutline > 24 then textoutline = 1 end
    if textoutline == 1 then
        local background = display.newRect(
            0, 0,
            display.contentWidth,
            display.contentHeight
        )
        background:setFillColor(254,254,254)
    end

    local myText = display.newText( text, 0, 0, nil, display.contentWidth/23 )

    myText:setTextColor(200,200,180)
    myText.x = math.floor(display.contentWidth/2)
    myText.y = (display.contentWidth/19) * textoutline - 5

    textoutline = textoutline + 1
    print(text)
end

-- 
-- HIDE STATUS BAR
-- 
display.setStatusBar( display.HiddenStatusBar )

-- 
-- MAIN TEST
-- 
local uuid = pubnub_obj:UUID()
textout("UUID")
textout(uuid)
