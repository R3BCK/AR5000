radio.log("Priority monitor started")
radio.setPriorityVfo("B", 130, true, 400)
radio.startPriorityMonitor()
while radio.isRunning() do radio.sleep(1000) end
radio.stopPriorityMonitor()