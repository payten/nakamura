#!/home/protected/bin/python
import web
urls = (
  '/', 'terms',
  '/terms', 'terms'
)
app = web.application(urls, globals())
class terms:
  def POST(self):
    import sys
    import re
    import simplejson as json
    from topia.termextract import extract
    extractor = extract.TermExtractor()
    #extractor.filter = extract.permissiveFilter
    extractor.filter = extract.DefaultFilter(singleStrengthMinOccur=1)
    def term_compare(x, y):
      if y[1]+y[2]*2 > x[1]+x[2]*2:
        return 1
      elif y[1]==x[1] and y[2]==x[2]:
        return 0
      else: # x<y
        return -1
    input = web.input(callback=None)
    content = input.context.lower()
    content = content.replace(u"\u201c", '"').replace(u"\u201d", '"').replace(u"\u2018", "'").replace(u"\u2019", "'").replace(u"\u2026", "")
    list = sorted(extractor(content), term_compare)
    list = list[:50]
    for i in range(len(list)-1, -1, -1):
      if len(list[i][0]) == 1 or list[i][2] > 2 or (list[i][0].find("http") >= 0) or not re.search('[a-z]', list[i][0]) or re.search('[0-9]', list[i][0]):
        list.remove(list[i])
      else:
        # prepend /tags/ to match expected input on server
        list[i] = list[i][0].strip()
    callback = input.callback
    pattern = r'[^a-zA-Z0-9 ]'
    for i in range(len(list)-1, -1, -1):
      if re.search(pattern, list[i]):
        list.remove(list[i])
    if (len(sys.argv) > 2):
      length = int(sys.argv[2])
      if (len(list) > length):
        list = list[:length]
    list = json.dumps(list, indent=4)
    if callback and re.match('^[a-zA-Z0-9._\[\]]+$', callback):
      return callback + '(' + list + ')'
    else:
      return list
    #return json.dumps(list, indent=4)
    #return json.dumps(extractor(i.context).sort(term_compare))
if __name__ == "__main__":
  app.run()
