package com.samliao.findpacket

object Carriers {
    val all = arrayOf(
        Carrier("黑貓宅急便", "https://www.t-cat.com.tw/inquire/trace.aspx", "#ContentPlaceHolder1_txtQuery1"),
        Carrier("中華郵政", "https://postserv.post.gov.tw/pstmail/main_mail.html"),
        Carrier("7-11 交貨便 / 取貨便", "https://eservice.7-11.com.tw/e-tracking/search.aspx", "#txtProductNum"),
        Carrier("全家 FamiPort 店到店", "https://fmec.famiport.com.tw/FP_Entrance/QueryBox", "input[name='orderno']"),
        Carrier("萊爾富", "https://www.hilife.com.tw/shipment-status"),
        Carrier("OK 超商", "https://ecservice.okmart.com.tw/Tracking/Search", "#inputOdNo"),
        Carrier("ezShip", "https://www.ezship.com.tw/receiver_query/ezship_query_shipstatus_2017.jsp"),
        Carrier("蝦皮店到店", "https://spx.tw/"),
        Carrier("台灣宅配通", "https://query2.e-can.com.tw/ECAN_APP/search.shtm", "input[name='txtMainID']"),
        Carrier("新竹物流", "https://www.hct.com.tw/Search/SearchGoods_n.aspx", "#ctl00_ContentFrame_txtpKey"),
        Carrier("嘉里大榮", "https://www.express.com.tw/tools/positchecking.aspx"),
        Carrier("便利帶", "https://www.25431010.tw/tracking", "input[name='barcode1']"),
        Carrier("順豐速運", "https://htm.sf-express.com/tw/tc/dynamic_function/waybill/"),
        Carrier("FedEx", "https://www.fedex.com/zh-tw/tracking.html"),
        Carrier("DHL", "https://www.dhl.com/tw-zh/home/tracking.html"),
        Carrier("UPS", "https://www.ups.com/track?loc=zh_TW"),
        Carrier("TNT", "https://www.tnt.com/express/zh_tw/site/shipping-tools/tracking.html"),
        Carrier("DPEX", "https://dpex.com/track-and-trace/", "#trackingNum"),
        Carrier("4PX 遞四方", "https://track.4px.com/"),
        Carrier("EMS 中國郵政", "https://www.ems.com.cn/english.html"),
        Carrier("圓通 YTO", "https://www.yto.net.cn/English/en/index.html"),
    )
}
