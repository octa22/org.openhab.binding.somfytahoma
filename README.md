# org.openhab.binding.somfytahoma
Somfy Tahoma binding for OpenHAB v1.x

Currently supports controlling of rollershutters & actionbindings

# build
copy __org.openhab.binding.somfytahoma__ directory to __binding__ directory of OpenHAB source code (https://github.com/openhab/openhab)

build using maven (mvn clean install)

# install
copy __gson-2.3.1.jar__ to __addons__ directory of OpenHAB (search internet or download here: http://central.maven.org/maven2/com/google/code/gson/gson/2.3.1/gson-2.3.1.jar)

copy target file __org.openhab.binding.somfytahoma.jar__ to __addons__ directory of OpenHAB distribution

#usage
this binding supports auto discovery of possible items to bind - both rolleshutters and actionbindings during binding initialization
e.g.
```
2016-09-13 09:58:08.014 [INFO ] [.s.internal.SomfyTahomaBinding] - Found unbound Somfy Tahoma RollerShutter(s): 
	Name: Fr. okno leva URL: io://1234-4519-8041/11452832
	Name: Fr. okno prava URL: io://1234-4519-8041/14133531
	Name: Herna URL: io://1234-4519-8041/2184165
	Name: Velux JV URL: io://1234-4519-8041/2418462
	Name: Detsky JV URL: io://1234-4519-8041/3802644
	Name: Detsky JZ URL: io://1234-4519-8041/4883641
	Name: Jidelna URL: io://1234-4519-8041/6250911
	Name: Kuchyn prava URL: io://1234-4519-8041/6472950
	Name: Loznice URL: io://1234-4519-8041/6865356
	Name: Obyvaci pokoj URL: io://1234-4519-8041/6965109
	Name: Velux JZ URL: io://1234-4519-8041/7735043
2016-09-13 09:58:08.065 [INFO ] [.s.internal.SomfyTahomaBinding] - Found unbound Somfy Tahoma action group(s): 
	Name: 1.NP nahoru URL: actiongroup:2104c46f-478d-4536-956a-10bd93b5dc55
	Name: 2.NP nahoru URL: actiongroup:712c0019-b422-4984-b4da-208e249c571c
	Name: 2.NP dolu URL: actiongroup:e201637b-de3b-4118-b7af-5693811a953c
```

The **io:/...** and **actiongroup:...** is everything what you need for the binding
Only RollerShutter, Dimmer and Switch item are currently supported
Actiongroups are executed when ON command is received


#example
items file:
```
Rollershutter Roleta1 "Roleta [%d %%]" (LivingRoom) {somfytahoma="io://1234-4519-8041/11452832", autoupdate="false"
Dimmer Roleta1D "Roleta dimmer [%.1f]" (LivingRoom) {somfytahoma="io://1234-4519-8041/11452832"}
Switch Rolety1NPDolu "Rolety 1NP dolu" (FirstFloor) {somfytahoma="actiongroup:e201637b-de3b-4118-b7af-5693811a953c"}
```

