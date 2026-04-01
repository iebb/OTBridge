REPLACE="
"

set_perm_recursive $MODPATH 0 0 0755 0644
set_perm_recursive $MODPATH/system/priv-app/ee.nekoko.nbridge 0 0 0755 0644
chcon -R u:object_r:system_file:s0 $MODPATH/system/priv-app/ee.nekoko.nbridge
set_perm $MODPATH/system/etc/permissions/privapp-permissions-ee.nekoko.nbridge.xml 0 0 0644
