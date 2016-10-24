#!/usr/bin/python2.7

import json
import re


_JAVA_PACKAGE = 'com.mikesamuel.cil.ast'


_TOKEN_RE = re.compile(
    '|'.join([
        r'//[^\r\n]*',  # Line comment
        r'/[*](?:[^*/]|[*]+(?!=[/*])|[/])*[*]+/',  # Block comment
        r'[ \t]+',  # Whitespace.  Indentation is significant
        r'[\r\n]+',  # Line breaks
        r'"(?:[^"\\]*|\\.)*"',  # Quoted string
        r'[A-Za-z_$]\w*',  # Word
        r'[\[\]\{\}:.]',  # Punctuation
        r'.',  # Other
    ]))

_LINE_BREAK_RE = re.compile(r'(\r\n?|\n)')

_NON_IDENT_CHAR = re.compile(r'[^A-Za-z0-9_$]+')
_UNDERSCORE_NEEDED = re.compile(r'^(?![a-zA-Z_$])|__+')

_TOK_SPACE = 0
_TOK_LINEBREAK = 1
_TOK_COMMENT = 2
_TOK_STRING = 3
_TOK_IDENT = 4
_TOK_OTHER = 5

_PTREE_NAME_TO_KIND = {
    None: 'PTree.Kind.SEQUENCE',
    '{}': 'PTree.Kind.REPEATED',
    '[]': 'PTree.Kind.OPTIONAL',
    'lit': 'PTree.Kind.LITERAL',
    'ref': 'PTree.Kind.REFERENCE',
    }

def _is_ident_start(c):
    return ((('A' <= c and c <= 'Z')
             or ('a' <= c and c <= 'z')
             or (c in ('_', '$'))))

def _is_ident_part(c):
    return ('0' <= c and c <= '9') or _is_ident_start(c)

def _classify_token((text, _)):
    n = len(text)
    if n == 0:
        return _TOK_SPACE
    t0 = text[0]
    if t0 == '"':
        return _TOK_STRING
    if t0 == '/' and n > 1:
        t1 = text[1]
        if t1 in ('*', '/'):
            return _TOK_COMMENT
    if t0 in (' ', '\t'):
        return _TOK_SPACE
    if t0 in ('\r', '\n'):
        return _TOK_LINEBREAK
    if _is_ident_start(t0):
        return _TOK_IDENT
    return _TOK_OTHER


_PUNC_TO_ALNUM = {
    '.': ('dot', {'.': (None, {'.': ('ellip',)})}),
    '[': ('ls',),
    ']': ('rs',),
    '(': ('lp',),
    ')': ('rp',),
    '{': ('lc',),
    '}': ('rc',),
    '&': ('amp', {'&': ('amp2',)}),
    '|': ('pip', {'|': ('pip2',)}),
    '<': ('lt', {'<': ('lt2', {'<': ('lt3',)})}),
    '>': ('gt', {'>': ('gt2', {'>': ('gt3',)})}),
    ',': ('com',),
    '?': ('qm',),
    ';': ('sem',),
    '*': ('str',),
    '=': ('eq',),
    '!': ('bng',),
    '@': ('at',),
    '/': ('fwd',),
    '\\':('bck',),
    ':': ('cln',),
    '-': ('dsh', {'>': ('arr',)}),
    '^': ('hat',),
    '~': ('tld',),
    '%': ('pct',),
    '+': ('pls',),
    '#': ('hsh',),
    '"': ('dq',),
    '\'':('sq',),
    '`': ('tck',),
}

_BRACKET_OTHERS = {
    '(': ')',
    ')': '(',
    '{': '}',
    '}': '{',
    '[': ']',
    ']': '[',
    }

def _to_alnum(s, i):
    """
    Returns an alphanumeric string describing the punctuation in s[i:]
    and the end of the characters described.
    """

    n = len(s)
    if i < n and _is_ident_part(s[i]):
        e = i + 1
        while e < n and _is_ident_part(s[e]):
            e += 1
        return (s[i:e], e)

    best = None
    current = _PUNC_TO_ALNUM
    while i < n and current is not None:
        next_trie = current.get(s[i])
        if next_trie is None:
            break
        assert type(next_trie) is tuple
        desc = next_trie[0]
        if desc is not None:
            best = (desc, i + 1)
        if len(next_trie) == 1:
            break
        current, i = next_trie[1], (i + 1)
    return best

def _java_str_lit(s):
    if type(s) is str:
        s = s.decode('UTF-8')
    assert type(s) is unicode
    return json.dumps(s)

_UNDERSCORE_IDENT_WORD_BREAK = re.compile(r'^_+([A-Za-z])|(?:^|_+)([A-Za-z])')
def _underscores_to_upper_camel(s):
    return _UNDERSCORE_IDENT_WORD_BREAK.sub(
        lambda m: (m.group(1) and '_%s' % m.group(1) or m.group(2)).upper(),
        s)

_CAMEL_SHIFT = re.compile(r'([a-z0-9_$])([A-Z])')
def _camel_to_underscores(s):
    return _CAMEL_SHIFT.sub(
        lambda m: '%s_%s' % (m.group(1), m.group(2)),
        s).upper()

def _jsdoc_of(s, prefix = ' * '):
    return (
        s.replace('&', '&amp;').replace('<', '&lt;')
         .replace('>', '&gt;').replace('*', '&#42;')
         .replace('\n', '\n%s' % prefix))

def _tokens_to_text(toks):
    return ' '.join([t[0] for t in toks])


def process_grammar(
        grammar_text,
        source_file_exists,
        emit_java_file,
        dot_out=None,
        verbose=True):
    """
    grammar_text : string -- See ../resources/jsl-19.txt
    source_file_exists : function --
        given a java file true iff it exists under the source directory in the
        right sub-directory for _JAVA_PACKAGE
    emit_java_file : function -- given unqualified name and java_source_code
        writes to a .java file in the generated source directory.
    """

    line_number, column_number, char_count = 0, 0, 0

    # Split into (token_text, position)
    tokens = []
    for token_text in _TOKEN_RE.findall(grammar_text):
        tokens.append((token_text, (line_number, column_number, char_count)))
        for token_text_part in _LINE_BREAK_RE.split(token_text):
            if token_text_part == '':
                continue
            if token_text_part[0] in ('\n', '\r'):
                line_number += 1
                column_number = 0
            else:
                column_number += len(token_text_part)
        char_count += len(token_text)

    assert char_count == len(grammar_text)  # All chars matched by _TOKEN_RE.

    if verbose:
        print '\nTOKENS'
        for t in tokens:
            print '%r' % (t,)

    # filter out whitespace and comments
    def is_significant(tok):
        return _classify_token(tok) not in (
            _TOK_SPACE, _TOK_COMMENT, _TOK_LINEBREAK)

    filtered_tokens = [t for t in tokens if is_significant(t)]
    if verbose:
        print '\nFILTERED TOKENS'
        for t in filtered_tokens:
            print '%r' % (t,)

    def starts_line((_, pos)):
        (_, column, _) = pos
        return column == 0
    def has_text((text, _), match):
        return text == match
    def is_ident(tok):
        return _classify_token(tok) == _TOK_IDENT

    # We start by splitting based on gross-structure and progress to fine
    # structure so that given an input like
    #
    # (chapter=ChapterName)
    # ProductionName:
    #   {Variant} One
    #
    # we end up with a structure like
    #
    # [
    #   {
    #     'name': 'ChapterName',
    #     'prods':
    #     [
    #       {
    #         'name': 'ProductionName',
    #         'variants':
    #         [
    #           {
    #             'name': 'VariantOne',
    #             'ptree':
    #             [
    #               {
    #                 'name': '{}',
    #                 'ptree':
    #                 [
    #                   { ... }
    # ...
    #
    # The layers of structure are
    # 1. Chapters based on the (chapter=Identifier) markings
    #    used to establish the base node type for productions.
    # 2. Productions which have the syntax
    #    LeftHandSide:
    #        RightHandSide0
    #        RightHandSide1
    # 3. Variants where each indented line after a production
    #    declaration is a variant.  The production is semantically
    #    the analytic disjunction of its variants.
    # 4. Grammar nodes where
    #    {...} means zero or more.
    #    [...] means one or more
    #    Foo   is a reference to a production by that name.
    #    "foo" matches the literal text "foo".

    def split_at(ls, splitter):
        start = 0
        items = []
        n = len(ls)
        for i in xrange(1, n):
            item = splitter(ls, start, i)
            if item is not None:
                items.append(item)
                start = i
        item = splitter(ls, start, n)
        if item is not None:
            items.append(item)
        return items

    def make_ptree(toks):
        """
        Matches bracket operators to group the right-hand-side of a
        grammar production variant into a recursive-expression form.
        """
        def make_node(toks, i):
            tok = toks[i]
            classification = _classify_token(tok)
            if classification == _TOK_STRING:
                return ({ 'name': 'lit', 'pleaf': tok }, i + 1)
            if classification == _TOK_IDENT:
                return ({ 'name': 'ref', 'pleaf': tok }, i + 1)
            if tok[0] in ('(', '[', '{'):
                end_bracket = _BRACKET_OTHERS[tok[0]]
                j, n = i + 1, len(toks)
                ptree = []
                while True:
                    if j == n:
                        raise SyntaxError(repr(toks))
                    if has_text(toks[j], end_bracket):
                        return (
                            {
                                'name': '%s%s' % (tok[0], end_bracket),
                                'ptree': ptree
                            },
                            j + 1
                        )
                    else:
                        sub_node, j = make_node(toks, j)
                        ptree.append(sub_node)
            raise SyntaxError(repr((tok, toks)))
        ptree = []
        i, n = 0, len(toks)
        while i < n:
            node, j = make_node(toks, i)
            assert j > i
            ptree.append(node)
            i = j
        return ptree

    def line_of((_, pos)):
        ln, _, _ = pos
        return ln
    def variant_maker():
        names_used = set()
        def get_variant_name(toks):
            base_name_parts = []
            for tok in toks:
                if _classify_token(tok) == _TOK_STRING:
                    str_content = tok[0][1:-1]
                    str_index = 0
                    while str_index < len(str_content):
                        alnum = _to_alnum(str_content, str_index)
                        if alnum is None:
                            str_index += 1
                        else:
                            alnum_part, str_index = alnum
                            base_name_parts.append(alnum_part)
                else:
                    base_name_parts.append(_NON_IDENT_CHAR.sub('', tok[0]))
            base_name_parts = [x for x in base_name_parts if x]
            # Now we have parts that contain the names of child productions
            # and descriptions of literal text.
            # Normalize underscores and turn it into one nice big
            # UpperCamelCase identifier.
            base_name = _UNDERSCORE_NEEDED.sub('_', '_'.join(base_name_parts))
            base_name = _underscores_to_upper_camel(base_name or 'epsilon')
            counter = 0
            name = base_name
            while name in names_used:
                counter += 1
                name = '%s$%d' % (base_name, counter)
            names_used.add(name)
            return name
        def maybe_make_variant(toks, s, e):
            if e == len(toks) or line_of(toks[s]) != line_of(toks[e]):
                sub_toks = toks[s:e]
                name = get_variant_name(sub_toks)
                return {
                    'name': name,
                    'ptree': make_ptree(sub_toks)
                    }
            return None

        return maybe_make_variant

    def get_prod_name(toks, i):
        if (i + 1 < len(toks)
            and starts_line(toks[i])
            and is_ident(toks[i])
            and has_text(toks[i+1], ':')):
            return toks[i][0]
        return None
    def maybe_make_prod(toks, s, e):
        if e > s:
            if e == len(toks) or get_prod_name(toks, e) is not None:
                name = get_prod_name(toks, s)
                sub_toks = name is not None and toks[s + 2:e] or toks[s:e]
                return {
                    'name': name or 'Unknown',
                    'toks': sub_toks,
                    'variants': split_at(sub_toks, variant_maker())
                    }
        return None

    def get_chapter_name(toks, i):
        if (i + 4 < len(toks)
            and starts_line(toks[i]) and has_text(toks[i], '(')
            and has_text(toks[i+1], 'chapter')
            and has_text(toks[i+2], '=')
            and is_ident(toks[i+3])
            and has_text(toks[i+4], ')')):
            return toks[i+3][0]
        return None
    def maybe_make_chapter(toks, s, e):
        if e > s:
            if e == len(toks) or get_chapter_name(toks, e) is not None:
                name = get_chapter_name(toks, s)
                sub_toks = name is not None and toks[s + 5:e] or toks[s:e]
                return {
                    'name': name or 'Unknown',
                    'toks': sub_toks,
                    'prods': split_at(sub_toks, maybe_make_prod)
                    }
        return None

    grammar = split_at(filtered_tokens, maybe_make_chapter)

    if verbose:
        import pprint
        print '\nGRAMMAR'
        for chapter in grammar:
            print '\tChapter %s' % chapter['name']
            for prod in chapter['prods']:
                print '\t\tProd %s' % prod['name']
                for variant in prod['variants']:
                    print '\t\t\tVariant %s' % variant['name']
                    pprinter = pprint.PrettyPrinter(indent=2, width=68)
                    pprinted_variant = pprinter.pformat(variant['ptree'])\
                                               .replace('\n', '\n\t\t\t\t')
                    print '\t\t\t\t%s' % pprinted_variant

    def create_enum_members():
        enum_members = []
        for chapter in grammar:
            base_type = 'Base%sNode.class' % chapter['name']
            for prod in chapter['prods']:
                enum_members.append(
                    '''
  /** <code>%(jsdoc)s</code> */
  %(name)s(
      %(base_type)s, %(name)sNode.Variant.class,
      PTree.nodeWrapper(%(name_str)s, %(name)sNode.Variant.class)),
''' % {
    'jsdoc': _jsdoc_of(' '.join([t[0] for t in prod['toks']])),
    'name': prod['name'],
    'name_str': _java_str_lit(prod['name']),
    'base_type': base_type,
    })
        return enum_members

    # Produce an enum with an entry for each production.
    emit_java_file(
        'NodeType',

        '''
package %(package)s;

import com.mikesamuel.cil.parser.ParSer;
import com.mikesamuel.cil.parser.ParSerable;
import com.mikesamuel.cil.ptree.PTree;

/**
 * A Java language production.
 */
@javax.annotation.Generated(%(generator)s)
public enum NodeType implements ParSerable {
%(members)s
  ;

  private final Class<? extends BaseNode> nodeBaseType;
  private final Class<? extends Enum<? extends NodeVariant>> variantType;
  private final ParSerable parSerable;

  NodeType(
      Class<? extends BaseNode> nodeBaseType,
      Class<? extends Enum<? extends NodeVariant>> variantType,
      ParSerable parSerable) {
    this.nodeBaseType = nodeBaseType;
    this.variantType = variantType;
    this.parSerable = parSerable;
  }

  /**
   * The base type for this node.
   * This establishes where in the gross structure of a compilation unit
   * nodes of this kind can appear.
   */
  public Class<? extends BaseNode> getNodeBaseType() {
    return nodeBaseType;
  }
  /**
   * Type that allows enumeration over the syntactic variants.
   */
  public Class<? extends Enum<? extends NodeVariant>> getVariantType() {
    return variantType;
   }
  /**
   * A parser/serializer instance that operates on nodes of this kind.
   */
  @Override
  public ParSer getParSer() {
    return parSerable.getParSer();
  }
}
        ''' % {
            'package': _JAVA_PACKAGE,
            'generator': _java_str_lit(__file__),
            'members': ''.join(create_enum_members()),
        })

    def for_each_chapter(f):
        for chapter in grammar:
            f(chapter)

    def for_each_prod(f):
        def g(chapter):
            for prod in chapter['prods']:
                f(chapter, prod)
        for_each_chapter(g)

    def for_each_variant(f):
        def g(chapter, prod):
            for variant in prod['variants']:
                f(chapter, prod, variant)
        for_each_prod(g)

    def assignforlambda(obj, key, val):
        obj[key] = val

    prods_by_name = {}
    for_each_prod(lambda c, p: assignforlambda(prods_by_name, p['name'], p))

    # We need to know which productions can match the empty string so we
    # can reliably identify calls which might be left-recursive.
    # We follow each possible branch from a production to see whether it is
    # epsilon-matching.  We could eliminate cyclic branches if we knew whether
    # a branch was left-recursive, but we don't know that yet.
    def compute_empty_matching():
        # We define a production as empty-matching if any variant of it is
        # empty-matching.

        # We keep track of a contingency graph from variants to productions.
        # For each variant, it is either not empty matching since it
        # requires a literal match, or it is empty matching only when all of
        # a set of non-optional dependencies are empty-matching.
        computed = set()
        empty_matching = set()

        computed.add('builtin')

        EMPTY_PATH = True
        NON_EMPTY_PATH = False
        CYCLIC_PATH = None

        # We try to detect cycles to determine whether to cache.
        CYCLE_DETECTED = True
        NO_CYCLE_DETECTED = False

        def walk_optional(pt, seen):
            # Do nothing since there is always an epsilon branch through
            # an option.
            return (EMPTY_PATH, NO_CYCLE_DETECTED)

        def walk_lit(pt, seen):
            return (pt['pleaf'][0] == '', NO_CYCLE_DETECTED)

        def walk_ref(pt, seen):
            target = pt['pleaf'][0]
            if target in computed:
                return (target in empty_matching, NO_CYCLE_DETECTED)
            if target in seen:
                return (CYCLIC_PATH, CYCLE_DETECTED)
            seen.add(target)
            result = walk_prod(prods_by_name[target], seen, False)
            seen.remove(target)
            return result

        def walk_cat(pt, seen):
            all_empty_or_cyclic = EMPTY_PATH  # True for empty concat
            cycle_detected = NO_CYCLE_DETECTED
            for cpt in pt['ptree']:
                e, c = walk_pt(cpt, seen)
                if e == NON_EMPTY_PATH:
                    all_empty_or_cyclic = NON_EMPTY_PATH
                    break
                if c == CYCLE_DETECTED:
                    cycle_detected = CYCLE_DETECTED
            return (all_empty_or_cyclic, cycle_detected)

        pt_handlers = {
            'ref': walk_ref,
            'lit': walk_lit,
            '()': walk_cat,
            '[]': walk_optional,
            # Treat Kleene star as optional Kleene plus
            '{}': walk_optional,
        }

        def walk_pt(pt, seen):
            return pt_handlers[pt['name']](pt, seen)

        def walk_prod(p, seen, topmost):
            pn = p['name']
            # A disjunction of zero options fails to match any string,
            # so it is safe to start with not-empty.
            overall_empty = None
            cycle = NO_CYCLE_DETECTED
            for v in p['variants']:
                e, c = walk_pt(
                    { 'name': '()', 'ptree': v['ptree'] },
                    seen)
                if c == CYCLE_DETECTED:
                    cycle = CYCLE_DETECTED
                if e == EMPTY_PATH:
                    overall_empty = True
                    break
                elif e == NON_EMPTY_PATH:
                    overall_empty = False
            if topmost or cycle == NO_CYCLE_DETECTED:
                computed.add(pn)
                if overall_empty:
                    empty_matching.add(pn)
            return (overall_empty, cycle)

        def start_walk(_, p):
            pn = p['name']
            if pn in computed:
                return
            seen = set()
            seen.add(pn)
            walk_prod(p, seen, True)

        for_each_prod(start_walk)
        return empty_matching

    empty_matching = compute_empty_matching()

    if verbose:
        #not_empty_matching_sorted = [pn for pn in prods_by_name.iterkeys()
        #                             if pn not in empty_matching]
        #not_empty_matching_sorted.sort()
        #print 'NOT EMPTY\n\t%s' % ('\n\t'.join(not_empty_matching_sorted))
        empty_matching_sorted = [pn for pn in prods_by_name.iterkeys()
                                 if pn in empty_matching]
        empty_matching_sorted.sort()
        print 'EMPTY MATCHING\n\t%s' % ('\n\t'.join(empty_matching_sorted))


    # Figure out what is left-recursive and compute a table mapping
    # (prod, variant) to the shortest LR (prod, variant) loop.
    left_recursion = {}
    for_each_prod(
        lambda c, p: assignforlambda(left_recursion, p['name'], {}))
    left_calls_per_variant = {}

    def left_call_names_of(p, v):
        pn = p['name']
        vn = v['name']
        key = (pn, vn)
        left_calls = left_calls_per_variant.get(key)
        if left_calls is not None:
            return left_calls
        left_calls = []
        def add_left_calls(pts):
            for pt in pts:
                if pt['name'] in ('{}', '[]', '()'):
                    add_left_calls(pt['ptree'])
                elif pt['name'] == 'ref':
                    name = pt['pleaf'][0]
                    if name == 'builtin':
                        break
                    left_calls.append(name)
                    if name not in empty_matching:
                        break
                else:
                    break
        add_left_calls(v['ptree'])
        if verbose and len(left_calls) >= 2:
            print 'MULTIPLE LEFT CALLS IN %s: %r' % (v['name'], left_calls)
        left_calls_per_variant[key] = tuple(left_calls)
        return left_calls

    def find_left_recursion(chapter, start_prod, start_variant):
        start_prod_name = start_prod['name']
        start_variant_name = start_variant['name']
        if start_variant_name in left_recursion[start_prod_name]:
            return
        seen = set()
        # Track the chain of calls that reach left-recursively
        variant_chain = []
        def find_left_calls(p, v):
            pn = p['name']
            vn = v['name']
            variant_chain.append((pn, vn))
            try:
                for left_call_name in left_call_names_of(p, v):
                    if left_call_name == start_prod_name:
                        if verbose:
                            print 'LR %s::%s via\n\t%s' % (
                                start_prod_name, start_variant_name,
                                '\n\t'.join(repr(x) for x in variant_chain))
                        left_recursion[start_prod_name][start_variant_name] = (
                            tuple(variant_chain))
                        return True
                    if left_call_name in seen:
                        return False
                    seen.add(left_call_name)
                    callee_p = prods_by_name[left_call_name]
                    for callee_v in callee_p['variants']:
                        if find_left_calls(callee_p, callee_v):
                            return True
            finally:
                variant_chain.pop()
            return False
        find_left_calls(start_prod, start_variant)
    for_each_variant(find_left_recursion)

    if verbose:
        print '\nLEFT_CALLS_PER_VARIANT'
        for ((pn, vn), lcs) in left_calls_per_variant.iteritems():
            if vn in left_recursion[pn]:
                print '\t%s.%s -> %r' % (pn, vn, lcs)
        print

    if dot_out is not None:
        def write_dot():
            prod_name_to_referents = {}
            def is_left(src, dest_name):
                src_name = src['name']
                for variant in src['variants']:
                    if dest_name in left_calls_per_variant[(src_name, variant['name'])]:
                        return True
                return False
            def walk_variant(c, p, v):
                pn = p['name']
                if pn not in prod_name_to_referents:
                    prod_name_to_referents[pn] = {}
                def walk(pt):
                    if 'ptree' in pt:
                        for spt in pt['ptree']:
                            walk(spt)
                    elif 'ref' == pt['name']:
                        callee_name = pt['pleaf'][0]
                        prod_name_to_referents[pn][callee_name] = (
                            prod_name_to_referents[pn].get(callee_name, False)
                            or is_left(p, callee_name))
                walk({ 'name': '()', 'ptree': v['ptree'] })
            for_each_variant(walk_variant)
            with open(dot_out, 'w') as dot_out_file:
                print >>dot_out_file, 'digraph nonterminals {'
                for pn, referent_to_is_left in prod_name_to_referents.iteritems():
                    print >>dot_out_file, '  %s;' % pn
                    for rn, is_left in referent_to_is_left.iteritems():
                        print >>dot_out_file, '  %s -> %s [color=%s];' % (pn, rn, is_left and 'blue' or 'black')
                print >>dot_out_file, '}'
        write_dot()
    # To handle left recursion, we need to know the shortest depth-first
    # cycle from a left call in a variant back to that variant.
    # See GrowTheSeed.java for the use.
    # This can be compute-intensive, so we precompute that here.
    def compute_shortest_left_call_loop_map():

        # We walk the grammar and either
        # 1. Build a solution recursively.  The return type for this is a
        #    tuple of the form ((prod_name, variant_name), rest_of_chain)
        # 2. The symbolic value CONTINUE which means keep looking.
        # 3. The symbolic value NO_MORE_LEFT_CALLS which means that no more
        #    left calls will be found on the current branch.
        NO_MORE_LEFT_CALLS = 0
        CONTINUE = 1

        # TODO: memoize this as it's likely to be used several times within
        # the same production.
        def shortest_left_call_chain(ptree, dest, visited):
            (dest_pn, dest_vn) = dest
            nm = ptree['name']
            if nm == '()':
                for cpt in ptree['ptree']:
                    r = shortest_left_call_chain(cpt, dest, visited)
                    if r != CONTINUE:
                        return r
                return CONTINUE
            elif nm in ('[]', '{}'):
                for cpt in ptree['ptree']:
                    r = shortest_left_call_chain(cpt, dest, visited)
                    if r == NO_MORE_LEFT_CALLS:
                        break
                    elif r != CONTINUE:
                        return r
                return CONTINUE
            elif nm == 'lit':
                assert ptree['pleaf'][0]
                return NO_MORE_LEFT_CALLS
            elif nm == 'ref':
                callee_name = ptree['pleaf'][0]
                print '\trecurse to %s' % callee_name
                if callee_name == 'builtin':
                    return NO_MORE_LEFT_CALLS
                callee = prods_by_name[callee_name]
                for callee_variant in callee['variants']:
                    key = (callee_name, callee_variant['name'])
                    print '\t\tfound key %r vs %r' % (key, dest)
                    if key == dest:
                        print '\t\tMATCH'
                        return (dest, None)
                    if callee_name not in visited:
                        visited.add(callee_name)
                        print '\t\tRECURSING to %r' % (key,)
                        r = shortest_left_call_chain(
                            { 'name': '()', 'ptree': callee_variant['ptree'] },
                            dest,
                            visited)
                        visited.remove(callee_name)
                        if r not in (CONTINUE, NO_MORE_LEFT_CALLS):
                            return (key, r)
                if callee_name in empty_matching:
                    return CONTINUE
                else:
                    return NO_MORE_LEFT_CALLS
            else:
                raise AssertionError(nm)
        DEBUGGED=[False]
        def find_shortest_left_call_chain_btw(src_pn, dest):
            visited = set()
            r = shortest_left_call_chain(
                { 'name': 'ref', 'pleaf': (src_pn, None) },
                dest, visited)
            if r in (CONTINUE, NO_MORE_LEFT_CALLS):
                return None
            left_call_chain = []
            while r is not None:
                left_call_chain.append(r[0])
                r = r[1]
            if not DEBUGGED[0] and len(left_call_chain) >= 3:
                DEBUGGED[0]=True
                print 'DEBUG LCC:\n\tleft_call_chain=%r\n\tsrc_pn=%r\n\tdest=%r\n\tr=%r' % (left_call_chain, src_pn, dest, r)
            assert left_call_chain[-1] == dest
            del left_call_chain[-1]
            return tuple(left_call_chain)

        results = {}
        for (dest, left_calls) in left_calls_per_variant.iteritems():
            dest_pn, dest_vn = dest
            if dest_vn not in left_recursion[dest_pn]:
                continue
            for callee in left_calls:
                key = (dest, callee)
                if key not in results:
                    if verbose:
                        print 'computing short left call chain %s -> %r' % (
                            callee, dest)
                    chain = find_shortest_left_call_chain_btw(callee, dest)
                    if chain is not None:
                        full_chain = [dest]
                        full_chain.extend(chain)
                        results[key] = full_chain
                        if verbose:
                            print '\t%r' % (full_chain,)
                    else:
                        raise Error('Failed to find LR markers for %r' % dest)
        return results

    shortest_left_call_loop_map = compute_shortest_left_call_loop_map()


    def ptree_to_java_builder(prod, pt, prefix):
        name = pt['name']
        prefix_plus = '    %s' % prefix
        extra_imports = set()
        if 'ptree' in pt:
            calls = []
            for subtree in pt['ptree']:
                sub_bldr, ei = ptree_to_java_builder(
                    prod, subtree, prefix=prefix_plus)
                extra_imports.update(ei)
                calls.append('%s.add(%s)' % (prefix_plus, sub_bldr))
            sub = '\n'.join(calls)
        else:
            (leaf_text, (leaf_ln, leaf_co, leaf_ci)) = pt['pleaf']
            if name == 'ref':
                if leaf_text == 'builtin':
                    sub = '%s.add(Tokens.%s)' % (
                        prefix_plus,
                        _camel_to_underscores(prod['name']))
                    leaf_value = 'Tokens.%sParSec' % name
                    extra_imports.add('com.mikesamuel.cil.ptree.Tokens')
                else:
                    sub = '%s.add(NodeType.%s)' % (prefix_plus, leaf_text)
            else:  # 'lit'
                leaf_value = _java_str_lit(leaf_text[1:-1])
                sub = '%s.leaf(%s, %d, %d, %d)' % (
                    prefix_plus, leaf_value,
                    leaf_ln, leaf_co, leaf_ci)

        return (
            (
                ''.join([
                    '',
                    '\n%(pp)sPTree.builder(%(kind)s)',
                    '\n%(sub)s',
                    '\n%(pp)s.build()'])
                % {
                    'kind': _PTREE_NAME_TO_KIND[name],
                    'pp': prefix_plus,
                    'sub': sub,
                }
            ), extra_imports
        )

    # For each production, produce a Node subclass.
    def write_node_class_for_production(chapter, prod):
        node_class_name = '%sNode' % prod['name']
        if source_file_exists('%s.java' % node_class_name):
            return

        def create_variant_members():
            variant_code = []
            extra_imports = set()
            for v in prod['variants']:
                ptree_builder, v_extra_imports = ptree_to_java_builder(
                    prod,
                    { 'name': None, 'ptree': v['ptree'] },
                    prefix='    ')
                extra_imports.update(v_extra_imports)
                is_lr = v['name'] in left_recursion[prod['name']]
                if is_lr: print 'LR: %s::%s' % (prod['name'], v['name'])
                variant_code.append(
                    (
                        '    /** */\n'
                        '    %(variant_name)s(%(ptree)s, %(is_lr)s),'
                        ) % {
                            'variant_name': v['name'],
                            'ptree': ptree_builder,
                            'is_lr': is_lr and 'true' or 'false',
                        })
            return ('\n'.join(variant_code)), extra_imports

        variant_members, extra_imports = create_variant_members()
        extra_import_stmts = ''.join(
            ['import %s;\n' % imp for imp in extra_imports])

        emit_java_file(
            node_class_name,
            '''
package %(package)s;

import com.mikesamuel.cil.parser.ParSer;
import com.mikesamuel.cil.parser.ParSerable;
import com.mikesamuel.cil.ptree.PTree;
%(extra_import_stmts)s
/**
 * Node for the JLS %(name)s production.
 *
 * <pre>
 * %(grammar_jsdoc)s
 * </pre>
 */
@javax.annotation.Generated(%(generator)s)
public final class %(node_class_name)s extends %(base_node_class)s {

  private %(node_class_name)s(
      Variant v, Iterable<? extends BaseNode> children, String literalValue) {
    super(NodeType.%(name)s, v, children, literalValue);
  }

  /** Mutable builder type. */
  public static BaseNode.Builder<%(node_class_name)s, Variant>
  builder(Variant v) {
    return new BaseNode.Builder<%(node_class_name)s, Variant>(
        NodeType.%(name)s, v) {
      @Override
      @SuppressWarnings("synthetic-access")
      public %(node_class_name)s build() {
        return new %(node_class_name)s(
            getVariant(), getChildren(), getLiteralValue());
      }
    };
  }

  @Override
  public Variant getVariant() {
    return (Variant) super.getVariant();
  }

  /** Variants of this node. */
  public enum Variant implements NodeVariant {
%(variant_members)s
    ;

    private final ParSerable parSerable;
    private final boolean isLeftRecursive;

    Variant(ParSerable parSerable, boolean isLR) {
      this.parSerable = parSerable;
      this.isLeftRecursive = isLR;
    }

    @Override
    public ParSer getParSer() { return parSerable.getParSer(); }

    @Override
    public NodeType getNodeType() { return NodeType.%(name)s; }

    @Override
    public boolean isLeftRecursive() { return isLeftRecursive; }

    @Override
    public String toString() {
      return getNodeType().name() + "." + name();
    }
  }
}
''' % {
    'package': _JAVA_PACKAGE,
    'extra_import_stmts': extra_import_stmts,
    'generator': _java_str_lit(__file__),
    'grammar_jsdoc': _jsdoc_of(_tokens_to_text(prod['toks'])),
    'node_class_name': node_class_name,
    'name': prod['name'],
    'base_node_class': 'Base%sNode' % chapter['name'],
    'variant_members': variant_members,
    })

    for_each_prod(write_node_class_for_production)

    # Write out the shortest (depth-first) LR paths so that our LR
    # handling code in the parser can use that to "grow the seed"
    # properly based on the variant taken.
    def write_lr_chains():
        lr_table_puts = []
        indent = '        '

        def java_variant(pn, vn):
            return '%sNode.Variant.%s' % (pn, vn)
        for (((pn, vn), callee_name), chain) in (
                shortest_left_call_loop_map.iteritems()):
            lr_table_puts.append(
                '.put(%s, NodeType.%s, ImmutableList.of(%s))' % (
                    java_variant(pn, vn),
                    callee_name,
                    ', '.join([java_variant(cpn, cvn)
                               for (cpn, cvn) in chain])))

        empty_matcher_args = ', '.join(
            ['NodeType.%s' % pn for pn in empty_matching])

        emit_java_file(
            'LeftRecursivePaths',
            '''
package %(package)s;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.ImmutableSet;

/**
 * Maps left-recursive variants to the shortest (depth-first)
 * cycle through the Grammar reference graph.
 */
@javax.annotation.Generated(%(generator)s)
public final class LeftRecursivePaths {

  /**
   * Maps variants to the shortest left-recursive cycles starting at that
   * variant.
   */
  public static final
  ImmutableTable<NodeVariant, NodeType, ImmutableList<NodeVariant>> LR_CYCLES;

  static {
    LR_CYCLES =
    ImmutableTable.<NodeVariant, NodeType, ImmutableList<NodeVariant>>builder()
%(indent)s%(lr_table_puts)s
        .build();
  }

  /**
   * Productions that can match the empty string.
   */
  public static final ImmutableSet<NodeType> EPSILON_MATCHERS =
      ImmutableSet.of(%(empty_matcher_args)s);
}
''' % {
    'package': _JAVA_PACKAGE,
    'generator': _java_str_lit(__file__),
    'indent': indent,
    'lr_table_puts': ('\n%s' % indent).join(lr_table_puts),
    'empty_matcher_args': empty_matcher_args,
})

    write_lr_chains()

if __name__ == '__main__':
    import argparse
    import os
    import os.path

    argparser = argparse.ArgumentParser()
    argparser.add_argument(
        '--srcdir',
        help=(
            'The root of the java source directory, e.g. basedir/src/main/java'
        ))
    argparser.add_argument(
        '--outdir',
        help=(
            'The root of the generated source directory for outputs,'
            ' e.g. basedir/target/generated-sources/parsehelpers'
        ))
    argparser.add_argument(
        '--dotout',
        help=('Path to graphviz .dot file that receives the non-terminal graph'
        ))
    argparser.add_argument(
        '-v',
        help='verbose',
        action='store_true')
    argparser.add_argument(
        'grammar_file', help='File containing the gramamr')
    args = argparser.parse_args()

    package_path = _JAVA_PACKAGE.split('.')

    def source_file_exists(rel_path):
        return os.path.isfile(
            os.path.join(
                os.path.join(args.srcdir, *package_path),
                rel_path))

    def emit_java_file(class_name, java_source):
        java_package_out_dir = os.path.join(args.outdir, *package_path)
        out_file = os.path.join(java_package_out_dir, '%s.java' % class_name)
        if not os.path.isdir(java_package_out_dir):
            os.makedirs(java_package_out_dir)
        with open(out_file, 'w') as out:
            out.write(java_source)

    process_grammar(
        grammar_text=open(args.grammar_file, 'r').read(),
        source_file_exists=source_file_exists,
        emit_java_file=emit_java_file,
        dot_out=args.dotout,
        verbose=args.v)
