function toggleSwitch(switchToToggle) {

    var url = "http://" + ip_address + "/apps/api/" + app_id + "/devices/" + switchToToggle.id + "/toggle?access_token=" + access_token;

    var xmlhttp = new XMLHttpRequest();
    // xmlhttp.onreadystatechange = function() {
    //     if (xmlhttp.readyState == 4 && xmlhttp.status == 200) {
    //         var hubitatDevice = JSON.parse(xmlhttp.responseText);
    //         document.getElementById(switchToToggle.id).innerHTML = hubitatDevice.label;
    //     }
    // };

    xmlhttp.open("GET", url, true);
    xmlhttp.send();

	var previousColor = document.getElementById(switchToToggle.id).style.backgroundColor;
    document.getElementById(switchToToggle.id).style.backgroundColor = "yellow";
    document.getElementById(switchToToggle.id).style.pointerEvents = "none";
    setTimeout(
        function () {
          //var randomColor = Math.floor(Math.random()*16777215).toString(16);
          document.getElementById(switchToToggle.id).style.backgroundColor = previousColor;
          document.getElementById(switchToToggle.id).style.pointerEvents = "all";
    },400);

}
