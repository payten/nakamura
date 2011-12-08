#! /usr/bin/env python
import pygraphviz as pgv
import base64, os, re, sys
import simplejson, urllib2
import telnetlib

"""Build a graph of bundle requirements (dependencies) of
org.sakaiproject.nakamura bundles from a running server.

Information is collected from the running server by accessing REST
endpoints in the Felix web console. The graph is used to seed
pygraphviz for producing a graph of the overall system, graphs for each
bundle's successors and each bundle's predecessors. Basic statistics
are collected and written to stats.log.

This script should run in less than 15s on a local server with all core
Nakamura bundles.

Script dependencies
-------------------
* pygraphviz

"""
host = 'http://localhost:8080'
username = 'admin'
password = 'admin'

def get_sakai_bundles():
    """Get a list of bundles that are create as part of Sakai OAE.

    Returns a list of bundle names.

    """
    req = urllib2.Request('{}/system/console/bundles/.json'.format(host))
    base64string = base64.encodestring('{}:{}'.format(username, password))[:-1]
    authheader =  'Basic {}'.format(base64string)
    req.add_header('Authorization', authheader)
    handle = urllib2.urlopen(req)
    data = simplejson.load(handle)['data']
    bundles = [d['symbolicName'] for d in data if d['symbolicName'].startswith('org.sakaiproject.nakamura') and not d['symbolicName'].endswith('uxloader')]
    return bundles

def get_package_reqs(bundle_id):
    """Get the requirements (imports) for a given bundle.

    Returns a list of bundle names.

    """
    req = urllib2.Request('{}/system/console/bundles/{}.json'.format(host, bundle_id))
    base64string = base64.encodestring(
                '{}:{}'.format(username, password))[:-1]
    authheader =  'Basic {}'.format(base64string)
    req.add_header('Authorization', authheader)
    handle = urllib2.urlopen(req)
    json = simplejson.load(handle)['data'][0]
    props = json['props']
    imports = []
    for prop in props:
        if prop['key'] != 'Imported Packages':
            continue
        for value in prop['value']:
            if not '>' in value or not '<' in value:
                continue
            start = value.rindex('>', 0, -1) + 1
            end = value.rindex('<')
            bundle_with_id = value[start:end]
            if bundle_with_id.startswith('org.sakaiproject.nakamura'):
                bundle = bundle_with_id[:bundle_with_id.index(' ')]
                if bundle not in imports:
                    imports.append(bundle)
        break
    return imports

def build_bundle_graph():
    """Build a graph_attr (nodes, edges) representing the connectivity
    within Sakai bundles

    """
    sakai_bundles = get_sakai_bundles()
    bundles = {}
    for bundle in sakai_bundles:
        reqs = get_package_reqs(bundle)
        bundles[bundle] = reqs
    return bundles

def draw_subgraph(name, graph, successors=True):
    """Draw (write to disk) a subgraph that starts with or ends with
    the specified node.

    Keyword arguments:
    name -- name of the node to focus on
    graph -- graph of paths between bundles
    filename -- filename to write subgraph to
    successors -- whether to lookup successors or predecessors

    """
    if successors:
        nbunch = graph.successors(name)
        filename = '{}.png'.format(name)
    else:
        nbunch = graph.predecessors(name)
        filename = '{}-pred.png'.format(name)

    if nbunch:
        nbunch.append(name)
        subgraph = graph.subgraph(nbunch)
        subgraph.layout(prog='dot')
        subgraph.draw('graphviz/{}'.format(filename))
    return len(nbunch)

def write_stats(stats):
    name_count = len(max(stats.keys(), key=lambda x:len(x)))
    succ_count = len('Successors')
    pred_count = len('Predecessors')
    line = '{} {} {}\n'.format('=' * name_count, '=' * succ_count, '=' * pred_count)

    log = open('graphviz/stats.log', 'w')
    log.write(line)
    log.write('{} {} {}\n'.format('Bundle'.ljust(name_count), 'Successors', 'Predecessors'))
    log.write(line)
    for bundle in sorted(stats.keys()):
        counts = stats[bundle]

        name = bundle.ljust(name_count)
        succ = str(counts[0]).rjust(succ_count)
        pred = str(counts[1]).rjust(pred_count)
        log.write('{} {} {}\n'.format(name, succ, pred))

    log.write(line)
    log.close()

def main():
    bundles_graph = build_bundle_graph()

    if not os.path.isdir('graphviz'):
        os.mkdir('graphviz')

    # print the whole graph
    graph = pgv.AGraph(data=bundles_graph, directed=True)
    graph.layout(prog='dot')
    graph.draw('graphviz/org.sakaiproject.nakamura.png')

    name_count = 0
    stats = {}
    for b_name, reqs in bundles_graph.items():
        s_count = draw_subgraph(b_name, graph)
        p_count = draw_subgraph(b_name, graph, False)

        stats[b_name] = (s_count, p_count)

    write_stats(stats)

if __name__ == '__main__':
    main()

