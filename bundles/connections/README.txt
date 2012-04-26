The Connections management service manages connections for the user.

1. Each use has their own contacts store.
In JCR this is
a:aaron/contacts
or in URL space
/~aaron/contacts

2. Within that store are connections.
a:aaron/contacts/ian
or in URL space
/~aaron/contacts/ian

In this case the node ian representing the connection between aaron  
and ian from the point of view of aaron.

There is a mirror node of the connection from the point of view of ian.
a:ian/contacts/aaron

3. On each of these nodes, there are 1..n properties, some are  
multivalue some are single value.
One of the properties is "sakai:state"

To create a new connection Aaron would POST to
/~aaron/contacts.invite.html with a post parameter of 'nico'
creates
a:aaron/contacts/nico
+
sakai:state = 'pending'
and
a:nico/contacts/aaron
+
sakai:state = 'invited'

and any other properties you care to use.

To accept Nico would POST to
/~nico/contacts.accept.html

any properties would appear on the .../nico/.../aaron/ node and the  
state of both nodes would be changed to 'connected'

To cancel Aaron would POST to
/~aaron/contacts/nico.cancel.html 

More examples:
Logged in as Aaron
/~aaron/contacts.invite.html with nico as a post parameter

/~aaron/contacts/nico.cancel.html

Logged in as Nico
/~nico/contacts.accept.html
/~nico/contacts.reject.html
/~nico/contacts.block.html
/~nico/contacts.ignore.html

If accepted Aaron
/~aaron/contacts/nico.remove.html

and Nico
/~nico/contacts.remove.html


For nico to see a contact
/~nico/contacts.html


To find all pendings we probably want
/~nico/contacts.invited.html

The default view
/~aaron/contacts.html


It uses a bigstore underneath the users public space protected by a
group to store the connections.
The group consists of all those who are able to see the connections
owned by the user.

When a connection is created a node is created in the bigstore that
contains the properties of the connection.
There are servlets that manage the state of these connections.

The big store implementation follows the pattern used in messaging.


Friends connections have connection status on each side of the connection.

connect request

 User A requests Connection to User B
 A record is added to user A and User B's space as pending
 User A's state marked as pending
 User B's state marked as requested
 

connect accept
 Only on a local connection state of requested and remote of pending.
 User B can accept the Connection in which case the status of both
   sides of the connection is set to connected.
 

connect reject
 Only on a local connection state of requested and remote of pending.
 User B can reject the Connection in which case the status of both
   sides of the connection is set to rejected.

connect ignore 
 Only on a local connection state of requested and remote of pending.
 User B can ignore the Connection in which case the status of the local
   side is marked ignored, and the remote side remains pending.

connect block 
 Only on a local connection state of requested and remote of pending.
 User B can block the Connection in which case the status of the local
   side is marked blocked, and the remote side remains pending.
 Once a local connection state is in blocked state, it cannot be
   changed except by an admin user.

connect cancel
 Only on a local connection state of pending and remote of requested or
   ignore.
 User A can cancel the setting both sides to canceled.
 
connect remove
 Only on a local connection = remote = connected | rejected
 
 
 
 Searching:
 
 There is a Search End points for each state available at 

/var/contacts/find.json?state={state}

Which return the contacts for the current user in the state requested. E.g.


 