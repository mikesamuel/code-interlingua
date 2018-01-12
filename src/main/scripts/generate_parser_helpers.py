#!/usr/bin/python2.7

import json
import re



_JAVA_BASE_PACKAGE = 'com.mikesamuel.cil.ast'

BUILTINS = ('builtin', 'any')

_TOKEN_RE = re.compile(
    '|'.join([
        r'//[^\r\n]*',  # Line comment
        r'/[*](?:[^*/]|[*]+(?!=[/*])|[/])*[*]+/',  # Block comment
        r'[ \t]+',  # Whitespace.  Indentation is significant
        r'[\r\n]+',  # Line breaks
        r'"(?:[^"\\]+|\\.)*"',  # Quoted string
        r'[A-Za-z_$]\w*',  # Word
        r'\(@\w+=[^"\'()]*\)',  # Annotation with value
        r'@\w+',  # Valueless annotation
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
_TOK_ANNOT = 5
_TOK_OTHER = 6

_PTREE_NAME_TO_KIND = {
    None: 'PTree.Kind.SEQUENCE',
    '()': 'PTree.Kind.SEQUENCE',
    '{}': 'PTree.Kind.REPEATED',
    '[]': 'PTree.Kind.OPTIONAL',
    'lit': 'PTree.Kind.LITERAL',
    'ref': 'PTree.Kind.REFERENCE',
    'nla': 'PTree.Kind.NEGATIVE_LOOKAHEAD',
    }

def _is_ident_start(c):
    return ((('A' <= c and c <= 'Z')
             or ('a' <= c and c <= 'z')
             or (c in ('_', '$'))))

def _is_ident_part(c):
    return ('0' <= c and c <= '9') or _is_ident_start(c)
def _is_annotation(tok_text):
    return tok_text[0] == '@' or tok_text[0:2] == '(@'

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
    if _is_annotation(text):
        return _TOK_ANNOT
    return _TOK_OTHER

def _first_upper(s):
    return '%s%s' % (s[0].upper(), s[1:])

_METADATA_THAT_NEEDS_CONTEXT = set([
    'String', 'java.lang.String',
    'Name', 'com.mikesamuel.cil.ast.meta.Name',
    'Name.Type', 'com.mikesamuel.cil.ast.meta.Name.Type',
    'boolean', 'byte', 'char', 'double', 'float', 'int', 'long', 'short',
    ])

# Short names for punctuation strings arranges as a Trie.
# This is used to derive descriptive variant names.
# The trie is represented as a map from punctuation strings to
# values that are pairs (identifier, suffix_trie) where the
# suffix tries match the characters after the key and are
# either None or Maps with the same structure as the Trie.
# A value which is a singleton tuple has an implicit None
# suffix trie.
# An identifier of None indicates that there is no name
# corresponding to the concatenation of the keys though
# the concatenation of the keys might be a prefix of a
# larger punctuation string.
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
    if len(toks) == 0:
        return ""
    parts = []
    last_line_no = toks[0][1][0]
    for (text, (ln, co, ch)) in toks:
        if ln != last_line_no:
            parts.append('\n')
            last_line_no = ln
        elif len(parts) != 0:
            parts.append(' ')
        parts.append(text)
    return ''.join(parts)


def _split_class(full_name):
    short_name = full_name[full_name.rfind('.') + 1:]
    return full_name, short_name


def process_grammar(
        grammar_name,
        grammar_customizations,
        grammar_text,
        source_file_exists,
        emit_java_file,
        dot_out=None,
        verbose=True):
    """
    grammar_name : string -- Specifies the subpackage under _JAVA_BASE_PACKAGE
        and a common prefix for node, node type, and node variant classes.
    gramamr_customizations: obj -- a data object of the form
        {
          "tokens": "foo.bar.Tokens",
          "postconds": "foo.bar.Postconds",
          "mixins": {...},
          "custom_node_content": {...}
        }

        The "tokens" value is the name of a class that includes a ParSer for
        each production with a single "builtin" variant.

        The "postconds" value similarly supports the @postcond annotation.

        The "mixins" map maps names of classes in the "mixins" subpackage
        of the mixin packages to data like
        {
          "state": [ ["FieldType", "fieldName"], ... ],
          "imports": ["foo.bar.FieldType", ...],
          "extends": ["MixinName"]
        }
        so that fields can be generated for node classes along with getters
        and setters.
        Absent fields are treated as equivalent to the empty list.

        The "custom_node_content" map maps production names to extra body
        content, and extra imports.
    grammar_text : string -- See ../resources/jsl-19.txt.
    source_file_exists : function --
        given a java file true iff it exists under the source directory in the
        right sub-directory for the output package
    emit_java_file : function -- given unqualified name and java_source_code
        writes to a .java file in the generated source directory.
    """

    java_package = '%s.%s' % (_JAVA_BASE_PACKAGE, grammar_name)
    mixins_package = '%s.mixins' % _JAVA_BASE_PACKAGE
    cn_prefix = '%s%s' % (grammar_name[0:1].upper(), grammar_name[1:])
    generator = _java_str_lit(os.path.relpath(__file__))

    mixin_defs = grammar_customizations.get('mixins', {})
    custom_node_content = grammar_customizations.get("custom_node_content", {})
    tokens_full_class_name, tokens_class_name = _split_class(
        grammar_customizations.get('tokens', '%s.Tokens' % java_package))
    postconds_full_class_name, postconds_class_name = _split_class(
        grammar_customizations.get('postconds', '%s.Postconds' % java_package))

    line_number, column_number, char_count = 1, 1, 0

    # Split into (token_text, position)
    tokens = []
    for token_text in _TOKEN_RE.findall(grammar_text):
        tokens.append((token_text, (line_number, column_number, char_count)))
        for token_text_part in _LINE_BREAK_RE.split(token_text):
            if token_text_part == '':
                continue
            if token_text_part[0] in ('\n', '\r'):
                line_number += 1
                column_number = 1
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
        return column == 1
    def has_text((text, _), match):
        return text == match
    def is_ident(tok):
        return _classify_token(tok) == _TOK_IDENT
    def same_line((a_text, (a_ln, a_co, a_ch)), (b_text, (b_ln, b_co, b_ch))):
        return a_ln == b_ln

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

    all_standard_lits = set()

    def make_ptree(is_standard, toks):
        """
        Matches bracket operators to group the right-hand-side of a
        grammar production variant into a recursive-expression form.

        is_standard : True iff this is part of the Java grammar proper.
               Not a non-standard extension like the template grammar.
        """
        def make_node(toks, i):
            tok = toks[i]
            classification = _classify_token(tok)
            if classification == _TOK_STRING:
                if is_standard:
                    all_standard_lits.add(tok[0])
                return ({ 'name': 'lit', 'pleaf': tok }, i + 1)
            if classification == _TOK_IDENT:
                return ({ 'name': 'ref', 'pleaf': tok }, i + 1)
            if tok[0] == '!':
                if i + 1 == len(toks):
                    raise SyntaxError('Expected content after "!"')
                sub_node, j = make_node(toks, i + 1)
                if sub_node is None:
                    raise SyntaxError(
                        'Expected content after "!" not annotation')
                return ({ 'name': 'nla', 'ptree': [sub_node] }), j
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
                        if sub_node is not None:
                            ptree.append(sub_node)
                        else:
                            break
            if _is_annotation(tok[0]):
                return None, i
            raise SyntaxError(repr((tok, toks)))
        ptree = []
        i, n = 0, len(toks)
        while i < n:
            node, j = make_node(toks, i)
            if node is None:
                assert j == i
                break
            assert j > i
            ptree.append(node)
            i = j
        return ptree, i

    def line_of((_, pos)):
        ln, _, _ = pos
        return ln
    def variant_maker(prod_name, is_standard):
        names_used = set()
        def get_variant_name(toks, annots):
            name_from_annotation = None
            for annot in annots:
                if annot[0].startswith('(@name='):
                    name_from_annotation = annot[0][len('(@name='):-1]
                    break
            if name_from_annotation is not None:
                name = name_from_annotation
            else:
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
                    elif has_text(tok, '!'):
                        if base_name_parts and base_name_parts[-1] == 'not':
                            base_name_parts[-1] = 'exp'
                        else:
                            base_name_parts.append('not')
                    elif _is_annotation(tok[0]):
                        break
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
            if name_from_annotation is not None and name != name_from_annotation:
                raise Exception('name from annotations %r is %s but needed disambiguation from %r'
                                % (annots, name_from_annotation, names_used))
            return name
        def maybe_make_variant(toks, s, e):
            if e == len(toks) or line_of(toks[s]) != line_of(toks[e]):
                sub_toks = toks[s:e]
                ptree, index_after = make_ptree(is_standard, sub_toks)
                annots = sub_toks[index_after:]
                assert all(_is_annotation(annot_text) for annot_text, annot_pos in annots), repr(annots)
                name = get_variant_name(sub_toks, annots)
                return {
                    'name': name,
                    'ptree': ptree,
                    'annots': annots,
                    }
            return None

        return maybe_make_variant

    def get_prod_name(toks, i):
        if (i + 1 < len(toks)
            and starts_line(toks[i])
            and is_ident(toks[i])
            and has_text(toks[i+1], ':')):
            return toks[i][0]
    def prod_maker(chapter_name):
        def maybe_make_prod(toks, s, e):
            if e > s:
                if e == len(toks) or get_prod_name(toks, e) is not None:
                    name = get_prod_name(toks, s)
                    if name is None:
                        ln, co, ch = toks[s][1]
                        print 'No name at %d:%d' % (ln, co)
                    sub_toks = name is not None and toks[s + 2:e] or toks[s:e]
                    annots = ()
                    if name is not None:
                        name_tok = toks[s]
                        for i in xrange(0, len(sub_toks)):
                            if not same_line(name_tok, sub_toks[i]):
                                annots = tuple(sub_toks[:i])
                                sub_toks = sub_toks[i:]
                                break
                    is_standard = all(x[0] != '@nonstandard' for x in annots)
                    return {
                        'name': name or 'Unknown',
                        'toks': sub_toks,
                        'variants': split_at(sub_toks, variant_maker(name, is_standard)),
                        'annots': annots,
                    }
            return None
        return maybe_make_prod

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
                    'prods': split_at(sub_toks, prod_maker(name))
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

    def write_chapter_list():
        members = []
        def add_member(c):
            members.append('  /** */\n  %s,' % c['name'])
        for_each_chapter(add_member)

        emit_java_file(
            '%sChapter' % cn_prefix,
            '''package %(package)s;

/**
 * The JLS chapter in which a node is defined.
 * Some productions have been moved around, so this is approximate.
 */
@javax.annotation.Generated(%(generator)s)
public enum %(cn_prefix)sChapter {
%(members)s
}
''' % {
    'package': java_package,
    'cn_prefix': cn_prefix,
    'generator': generator,
    'members': '\n'.join(members),
})

    write_chapter_list()

    def compute_reachable():
        reachable = set()
        def compute_reachable_from(pn):
            def walk_ptree(pt):
                if 'ptree' in pt:
                    for sub in pt['ptree']:
                        walk_ptree(sub)
                elif pt['name'] == 'ref':
                    ref_name = pt['pleaf'][0]
                    compute_reachable_from(ref_name)
            if pn not in reachable:
                reachable.add(pn)
                prod = prods_by_name.get(pn, None)
                if prod is not None:
                    for v in prod['variants']:
                        for pt in v['ptree']:
                            walk_ptree(pt)
        for pn, prod in prods_by_name.iteritems():
            if '@toplevel' in (annot[0] for annot in prod['annots']):
                compute_reachable_from(pn)
        return reachable

    reachable = compute_reachable()

    def prune_unreachable():
        unreachable = set(prods_by_name.keys()).difference(reachable)
        if verbose:
            # Dump a list of productions that are not reachable from @toplevel
            # prods
            for ur in unreachable:
                print 'UNREACHABLE: %s' % ur
        for ur in unreachable:
            del prods_by_name[ur]
        def prune(c):
            c['prods'] = tuple([p for p in c['prods'] if p['name'] in reachable])
        for_each_chapter(prune)
    prune_unreachable()

    def infer_intermediates():
        """
        infer @intermediate annotations to variants that are implicitly intermediate
        and convert @intermediate annotations to @delegate annotations.
        """
        def find_delegate(pt, force):
            name = pt['name']
            if name == 'ref':
                delegate = pt['pleaf'][0]
                if delegate in BUILTINS:
                    return False
                else:
                    return delegate
            elif name == 'lit':
                if force:
                    return None
                else:
                    return False
            elif name == 'nla':
                return None
            elif name in ('()', '{}', '[]'):
                delegate = None
                for sub in pt['ptree']:
                    found = find_delegate(sub, force)
                    if found is not None:
                        if found:
                            # At most one consumed nonterminal
                            if delegate is not None:
                                return False
                            else:
                                delegate = found
                        else:
                            return False
                return delegate
        def infer(c, p, v):
            annots = [a for a in v['annots'] if a[0] != '@intermediate']
            # Ignore literals if @intermediate is explicit
            force = len(annots) != len(v['annots'])
            delegate = find_delegate({ 'name': '()', 'ptree': v['ptree'] }, force)
            if force and not delegate:
                raise Exception('%s.%s has no delegate' % (p['name'], v['name']))
            if delegate:
                delegate_annot = '(@delegate=%s)' % delegate
                annots.append((delegate_annot, (0, 0, 0)))
                v['annots'] = annots
        for_each_variant(infer)
    infer_intermediates()

    def create_grammar_specific_base_types():
        emit_java_file(
            '%sBaseNode' % cn_prefix,

            '''
package %(package)s;

import com.mikesamuel.cil.ast.BaseNode;

/**
 * A node in a %(cn_prefix)s grammar syntax tree.
 */
public abstract class %(cn_prefix)sBaseNode
extends BaseNode<%(cn_prefix)sBaseNode,
                 %(cn_prefix)sNodeType,
                 %(cn_prefix)sNodeVariant> {

  %(cn_prefix)sBaseNode(%(cn_prefix)sNodeVariant variant) {
    super(variant);
  }

}
''' % {
    'package': java_package,
    'cn_prefix': cn_prefix,
})

        emit_java_file(
            '%sBaseInnerNode' % cn_prefix,

            '''
package %(package)s;

import com.mikesamuel.cil.ast.InnerNode;
import java.util.List;
import javax.annotation.Nullable;

/**
 * A %(cn_prefix)s grammar node that has children instead of token content.
 */
public abstract class %(cn_prefix)sBaseInnerNode
extends %(cn_prefix)sBaseNode
implements InnerNode<%(cn_prefix)sBaseNode, %(cn_prefix)sNodeType, %(cn_prefix)sNodeVariant> {
  private final MutableChildList<%(cn_prefix)sBaseNode> children =
      new MutableChildList<>();

  %(cn_prefix)sBaseInnerNode(
      %(cn_prefix)sNodeVariant variant,
      Iterable<? extends %(cn_prefix)sBaseNode> initialChildren) {
    super(variant);
    children.replaceChildren(initialChildren);
  }

  @Override
  public final MutableChildList<%(cn_prefix)sBaseNode> getMutableChildList() {
    return children;
  }

  @Override
  public final @Nullable String getValue() {
    return null;
  }

  @Override
  public final int getNChildren() {
    return children.getNChildren();
  }

  @Override
  public final %(cn_prefix)sBaseNode getChild(int i) {
    return children.getChild(i);
  }

  @Override
  public final List<%(cn_prefix)sBaseNode> getChildren() {
    return children.getChildren();
  }

  @Override
  public abstract %(cn_prefix)sBaseInnerNode shallowClone();

  @Override
  public abstract %(cn_prefix)sBaseInnerNode deepClone();

}
''' % {
    'package': java_package,
    'cn_prefix': cn_prefix,
})

        emit_java_file(
            '%sBaseLeafNode' % cn_prefix,

            '''
package %(package)s;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.mikesamuel.cil.ast.LeafNode;

/**
 * A node that has some token content instead of children.
 */
public abstract class %(cn_prefix)sBaseLeafNode
extends %(cn_prefix)sBaseNode
implements LeafNode<%(cn_prefix)sBaseNode, %(cn_prefix)sNodeType, %(cn_prefix)sNodeVariant> {
  private String value;

  %(cn_prefix)sBaseLeafNode(%(cn_prefix)sNodeVariant variant, String initialValue) {
    super(variant);
    setValue(initialValue);
  }

  @Override
  public final String getValue() {
    return value;
  }

  @Override
  public final void setValue(String newValue) {
    Preconditions.checkArgument(
        newValue != null && isValidValue(newValue), newValue);
    this.value = newValue;
  }

  @Override
  public final int getNChildren() {
    return 0;
  }

  @Override
  public final %(cn_prefix)sBaseNode getChild(int i) {
    throw new IndexOutOfBoundsException("" + i);
  }

  @Override
  public final ImmutableList<%(cn_prefix)sBaseNode> getChildren() {
    return ImmutableList.of();
  }
}
''' % {
    'package': java_package,
    'cn_prefix': cn_prefix,
})

        emit_java_file(
            '%sNodeVariant' % cn_prefix,

            '''
package %(package)s;

import com.mikesamuel.cil.ast.NodeVariant;

/**
 * A node variant for the %(cn_prefix)s grammar.
 */
public interface %(cn_prefix)sNodeVariant
extends NodeVariant<%(cn_prefix)sBaseNode, %(cn_prefix)sNodeType> {

  @Override
  default %(cn_prefix)sBaseLeafNode buildNode(String value) {
    throw new IllegalArgumentException(this + " is an inner node type");
  }

  @Override
  default %(cn_prefix)sBaseInnerNode buildNode(
      Iterable<? extends %(cn_prefix)sBaseNode> children) {
    throw new IllegalArgumentException(this + " is a leaf node type");
  }

}
''' % {
    'package': java_package,
    'cn_prefix': cn_prefix,
})

    create_grammar_specific_base_types()

    def create_enum_members():
        enum_members = []
        for chapter in grammar:
            for prod in chapter['prods']:
                enum_members.append(
                    '''
  /**
   * <pre>%(jsdoc)s</pre>
   */
  %(name)s(%(name)sNode.class, %(name)sNode.Variant.class, %(cn_prefix)sChapter.%(chapter_name)s),
''' % {
    'cn_prefix': cn_prefix,
    'jsdoc': _jsdoc_of(_tokens_to_text(prod['toks']), prefix = '   * '),
    'name': prod['name'],
    'name_str': _java_str_lit(prod['name']),
    'chapter_name': chapter['name'],
    })
        return enum_members

    def annotation_on_any_prod(annot_name):
        for prod in prods_by_name.itervalues():
            for (a, _) in prod['annots']:
                if annot_name == a:
                    return True
        return False
    if annotation_on_any_prod('@nonstandard'):
        is_non_standard = '%sNodeTypeTables.NONSTANDARD.contains(this)' % (
            cn_prefix)
    else:
        is_non_standard = 'false'

    # Produce an enum with an entry for each production.
    emit_java_file(
        '%sNodeType' % cn_prefix,

        '''
package %(package)s;

import com.mikesamuel.cil.ast.NodeType;
import com.mikesamuel.cil.ast.Grammar;
import com.mikesamuel.cil.parser.ParSer;
import com.mikesamuel.cil.parser.ParSerable;
import com.mikesamuel.cil.ptree.PTree;

/**
 * A Java language production.
 */
@javax.annotation.Generated(%(generator)s)
public enum %(cn_prefix)sNodeType
implements NodeType<%(cn_prefix)sBaseNode, %(cn_prefix)sNodeType> {
%(members)s
  ;

  private final Class<? extends %(cn_prefix)sBaseNode> nodeBaseType;
  private final Class<? extends Enum<? extends %(cn_prefix)sNodeVariant>> variantType;
  private final ParSerable parSerable;
  private final %(cn_prefix)sChapter chapter;

  %(cn_prefix)sNodeType(
      Class<? extends %(cn_prefix)sBaseNode> nodeBaseType,
      Class<? extends Enum<? extends %(cn_prefix)sNodeVariant>> variantType,
      %(cn_prefix)sChapter chapter) {
    this.nodeBaseType = nodeBaseType;
    this.variantType = variantType;
    this.parSerable = PTree.nodeWrapper(this);
    this.chapter = chapter;
  }

  /**
   * The base type for this node.
   * This establishes where in the gross structure of a compilation unit
   * nodes of this kind can appear.
   */
  @Override
  public Class<? extends %(cn_prefix)sBaseNode> getNodeBaseType() {
    return nodeBaseType;
  }
  /**
   * Type that allows enumeration over the syntactic variants.
   */
  @Override
  public Class<? extends Enum<? extends %(cn_prefix)sNodeVariant>> getVariantType() {
    return variantType;
  }
  /**
   * The JLS chapter in which this node is defined.
   * Approximate as some things have moved about.
   */
  public %(cn_prefix)sChapter getChapter() {
    return chapter;
  }

  /**
   * A parser/serializer instance that operates on nodes of this kind.
   */
  @Override
  public ParSer getParSer() {
    return parSerable.getParSer();
  }

  @Override
  public boolean isNonStandard() {
    return %(is_non_standard)s;
  }

  @Override
  public boolean isTopLevel() {
    return %(cn_prefix)sNodeTypeTables.TOPLEVEL.contains(this);
  }

  @Override
  public boolean isIdentifierWrapper() {
    return IdentifierWrappers.isIdentifierWrapper(this);
  }

  /** The grammar for %(cn_prefix)s nodes. */
  public static final Grammar<%(cn_prefix)sBaseNode, %(cn_prefix)sNodeType> GRAMMAR =
      GrammarImpl.INSTANCE;

  @Override
  public Grammar<%(cn_prefix)sBaseNode, %(cn_prefix)sNodeType> getGrammar() {
    return GRAMMAR;
  }
}
''' % {
    'package': java_package,
    'cn_prefix': cn_prefix,
    'generator': generator,
    'is_non_standard': is_non_standard,
    'members': ''.join(create_enum_members()),
})

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

        computed.update(BUILTINS)

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
            # Lookahead consumes no input.
            'nla': walk_optional,
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
                ptn = pt['name']
                if ptn in ('{}', '[]', '()'):
                    add_left_calls(pt['ptree'])
                elif ptn == 'ref':
                    name = pt['pleaf'][0]
                    if name in BUILTINS:
                        break
                    left_calls.append(name)
                    if name not in empty_matching:
                        break
                    elif ptn == 'nla':
                        continue
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
                    if dest_name in left_calls_per_variant[
                            (src_name, variant['name'])]:
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
                for pn, referent_to_is_left in (
                        prod_name_to_referents.iteritems()):
                    print >>dot_out_file, '  %s;' % pn
                    for rn, is_left in referent_to_is_left.iteritems():
                        print >>dot_out_file, '  %s -> %s [color=%s];' % (
                            pn, rn, is_left and 'blue' or 'black')
                print >>dot_out_file, '}'
        write_dot()

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
                if leaf_text in BUILTINS:
                    sub = '%s.add(%s.%s)' % (
                        prefix_plus, tokens_class_name,
                        _camel_to_underscores(prod['name']))
                    extra_imports.add(tokens_full_class_name)
                else:
                    sub = '%s.add(%sNodeType.%s)' % (
                        prefix_plus, cn_prefix, leaf_text)
            else:  # 'lit'
                leaf_value = _java_str_lit(leaf_text[1:-1])
                optional_token_merge_hazard = ''
                if (leaf_value == '">"'
                    and prod['name'] in ('TypeArguments', 'TypeParameters')):
                    optional_token_merge_hazard = ', true'
                sub = '%s.leaf(%s%s, %d, %d, %d)' % (
                    prefix_plus,
                    leaf_value, optional_token_merge_hazard,
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

    mixins_used = set()

    # For each production, produce a Node subclass.
    def write_node_class_for_production(chapter, prod):
        node_class_name = '%sNode' % prod['name']
        if source_file_exists('%s.java' % node_class_name):
            return
        variants = prod['variants']

        extra_imports = set((
            'com.mikesamuel.cil.ast.NodeI',
            'com.mikesamuel.cil.ast.meta.MetadataBridge',
            'com.mikesamuel.cil.parser.ParSer',
            'com.mikesamuel.cil.parser.ParSerable',
            'com.mikesamuel.cil.ptree.PTree',
        ))

        is_inner_node = not (len(variants) == 1
                             and variants[0]['name'] == 'Builtin')

        # extra code for the Node body.
        extra_code = []
        custom_code, custom_code_imports = custom_node_content.get(
            prod['name'], ('', ()))
        extra_code.append(custom_code)
        extra_imports.update(custom_code_imports)

        # extra code for the custom copyMetadataFrom method
        copy_code = []

        if is_inner_node:
            ctor_formals = 'Iterable<? extends %sBaseNode> children' % cn_prefix
            super_ctor_actuals = 'children'
            copy_ctor_actuals = 'source.getVariant(), source.getChildren()'
            build_node_calls = '''
    @Override
    public %(node_class_name)s buildNode(Iterable<? extends %(cn_prefix)sBaseNode> children) {
      return new %(node_class_name)s(this, children);
    }

    /** Constructs a node with this variant and the given children. */
    public %(node_class_name)s buildNode(%(cn_prefix)sBaseNode... children) {
      return buildNode(Arrays.asList(children));
    }
''' % {
    'node_class_name': node_class_name,
    'cn_prefix': cn_prefix,
}
            extra_imports.add('java.util.Arrays');
        else:
            ctor_formals = 'String value'
            super_ctor_actuals = 'value'
            copy_ctor_actuals = 'source.getVariant(), source.getValue()'
            build_node_calls = '''
    @Override
    public %(node_class_name)s buildNode(String value) {
      return new %(node_class_name)s(this, value);
    }
''' % { 'node_class_name': node_class_name }

        def create_variant_members():
            variant_code = []
            prod_matches_empty = prod['name'] in empty_matching
            annot_converters = {
                'name': None,
                'postcond': (
                    'Predicate<SList<Event>>',
                    ('com.mikesamuel.cil.event.Event',
                     'com.mikesamuel.cil.parser.SList',
                     'com.google.common.base.Predicate',
                     postconds_full_class_name),
                    lambda x: '%s.%s' % (postconds_class_name, x)
                ),
                'delegate': (
                    '%sNodeType' % cn_prefix,
                    (),
                    lambda x: '%sNodeType.%s' % (cn_prefix, x)
                ),
            }

            for v in variants:
                ptree_builder, v_extra_imports = ptree_to_java_builder(
                    prod,
                    { 'name': None, 'ptree': v['ptree'] },
                    prefix='    ')
                extra_imports.update(v_extra_imports)
                overridden_methods = []
                for (annot_text, _) in v['annots']:
                    if annot_text.startswith('(@name='): continue
                    annot_value, annot_name = None, None
                    override = None
                    if annot_text.startswith('('):
                        annot_name, annot_value = annot_text[2:-1].split('=')
                        annot_converter = annot_converters[annot_name]
                        if annot_converter is not None:
                            annot_type, annot_extra_imports, annot_value_conv = annot_converter
                            override = {
                                'type': annot_type,
                                'name': 'get%s' % _first_upper(annot_name),
                                'value': annot_value_conv(annot_value),
                            }
                            extra_imports.update(annot_extra_imports)
                    else:
                        annot_name = annot_text[1:]
                        override = {
                            'type': 'boolean',
                            'name': 'is%s' % _first_upper(annot_name),
                            'value': 'true'
                            }
                    if override is not None:
                        overridden_methods.append(
                            (
                                '      @Override\n'
                                '      public %(type)s %(name)s() {\n'
                                '        return %(value)s;\n'
                                '      }\n'
                            ) % override)

                variant_code.append(
                    (
                        '    /** */\n'
                        '    %(variant_name)s(%(ptree)s)%(overrides)s,'
                    ) % {
                        'variant_name': v['name'],
                        'ptree': ptree_builder,
                        'overrides': (
                            overridden_methods
                            and '{\n%s    }' % '\n'.join(overridden_methods)
                            or ''),
                    })
            return ('\n'.join(variant_code))

        variant_members = create_variant_members()

        mixins = set()
        for (annot_name, _) in prod['annots']:
            if annot_name.startswith('(@mixin='):
                mixins.update([x.strip() for x in annot_name[8:-1].split(',')
                               if len(x.strip())])
        declared_mixins = list(mixins)
        declared_mixins.sort()

        # expand mixins based on extended mixins.
        size_before = 0
        while len(mixins) != size_before:
            size_before = len(mixins)
            for mixin in list(mixins):
                mixins.update(mixin_defs[mixin].get("extends", ()))
        mixins = list(mixins)
        mixins.sort()

        for mixin in mixins:
            mixin_def = mixin_defs.get(mixin, ((), ()))
            mixin_fields = mixin_def.get('state', ())
            mixin_imports = mixin_def.get('imports', ())
            extra_imports.update(mixin_imports)
            if len(mixin_fields):
                extra_imports.add('%s.%s' % (mixins_package, mixin))
            copy_calls = []
            for mixin_type, mixin_field in mixin_fields:
                umixin_field = _first_upper(mixin_field)
                record = {
                    'node_class_name': node_class_name,
                    'mixin': mixin,
                    'mixin_field': mixin_field,
                    'umixin_field': umixin_field,
                    'mixin_type': mixin_type,
                    'bridge_name': (
                        mixin_type in _METADATA_THAT_NEEDS_CONTEXT
                        and umixin_field or mixin_type
                        )
                }
                extra_code.append(
                    ('  private %(mixin_type)s %(mixin_field)s;\n'
                     '\n'
                     '  @Override\n'
                     '  public final %(node_class_name)s set%(umixin_field)s(%(mixin_type)s new%(umixin_field)s) {\n'
                     '    this.%(mixin_field)s = new%(umixin_field)s;\n'
                     '    return this;\n'
                     '  }\n'
                     '\n'
                     '  @Override\n'
                     '  public final %(mixin_type)s get%(umixin_field)s() {\n'
                     '    return this.%(mixin_field)s;\n'
                     '  }\n')
                    % record)
                copy_calls.append(
                    ('      set%(umixin_field)s(bridge.bridge%(bridge_name)s('
                     '((%(mixin)s<?, ?, ?>) source).get%(umixin_field)s()));')
                    % record
                    )
            if len(copy_calls):
                copy_code.append('    %s {\n%s\n    }' % (
                    'if (source instanceof %(mixin)s)' % record,
                    '\n'.join(copy_calls)))

        mixin_ifaces = ''
        if mixins:
            mixin_ifaces = '\nimplements %s' % ', '.join(
                '%s%s' % (cn_prefix, t) for t in declared_mixins)
        mixins_used.update(mixins)

        extra_import_stmts = ''.join(
            ['import %s;\n' % imp for imp in sorted(extra_imports)])

        emit_java_file(
            node_class_name,
            '''
package %(package)s;

%(extra_import_stmts)s
/**
 * Node for the JLS %(name)s production.
 *
 * <pre>
 * %(grammar_jsdoc)s
 * </pre>
 */
@javax.annotation.Generated(%(generator)s)
public final class %(node_class_name)s extends %(base_node_class)s%(mixin_ifaces)s {

  /** */
  public %(node_class_name)s(Variant v, %(ctor_formals)s) {
    super(v, %(super_ctor_actuals)s);
  }

  /** Copy constructor. */
  public %(node_class_name)s(%(node_class_name)s source) {
    super(%(copy_ctor_actuals)s);
    copyMetadataFrom(source, MetadataBridge.Bridges.IDENTITY);
  }

%(extra_code)s

  @Override
  public %(node_class_name)s shallowClone() {
    return new %(node_class_name)s(this);
  }

  @Override
  public %(node_class_name)s deepClone() {
    return deepCopyChildren(shallowClone());
  }

  @Override
  public Variant getVariant() {
    return (Variant) super.getVariant();
  }

  @Override
  public void copyMetadataFrom(NodeI<?, ?, ?> source, MetadataBridge bridge) {
%(copy_code)s
    super.copyMetadataFrom(source, bridge);
  }

  /** Variants of this node. */
  public enum Variant implements %(cn_prefix)sNodeVariant {
%(variant_members)s
    ;

    private final ParSerable parSerable;

    Variant(ParSerable parSerable) {
      this.parSerable = parSerable;
    }

    @Override
    public ParSer getParSer() { return parSerable.getParSer(); }

    @Override
    public %(cn_prefix)sNodeType getNodeType() { return %(cn_prefix)sNodeType.%(name)s; }
%(build_node_calls)s

    @Override
    public String toString() {
      return getNodeType().name() + "." + name();
    }
  }
}
''' % {
    'package': java_package,
    'cn_prefix': cn_prefix,
    'extra_import_stmts': extra_import_stmts,
    'generator': generator,
    'grammar_jsdoc': _jsdoc_of(_tokens_to_text(prod['toks'])),
    'node_class_name': node_class_name,
    'name': prod['name'],
    'base_node_class': '%s%s' % (
        cn_prefix, is_inner_node and 'BaseInnerNode' or 'BaseLeafNode'),
    'variant_members': variant_members,
    'ctor_formals': ctor_formals,
    'super_ctor_actuals': super_ctor_actuals,
    'copy_ctor_actuals': copy_ctor_actuals,
    'extra_code': '\n'.join(extra_code),
    'copy_code': '\n'.join(copy_code),
    'mixin_ifaces': mixin_ifaces,
    'build_node_calls': build_node_calls,
    })

    for_each_prod(write_node_class_for_production)

    def write_bound_mixins():
        for mixin in mixins_used:
            super_mixins = mixin_defs[mixin].get("extends", ())
            emit_java_file(
                '%s%s' % (cn_prefix, mixin),
                '''
package %(package)s;

import %(mixins_package)s.%(mixin)s;

/**
 * @see %(mixin)s
 */
@javax.annotation.Generated(%(generator)s)
public interface %(cn_prefix)s%(mixin)s
extends %(mixin)s<%(cn_prefix)sBaseNode, %(cn_prefix)sNodeType, %(cn_prefix)sNodeVariant>%(super_bound_mixins)s {
  // Just binds variables.
}
''' % {
    'package': java_package,
    'mixins_package': mixins_package,
    'cn_prefix': cn_prefix,
    'generator': generator,
    'mixin': mixin,
    'super_bound_mixins': ''.join([
        ', %s%s' % (cn_prefix, super_mixin) for super_mixin in super_mixins])
    })

    write_bound_mixins()

    def write_literal_tokens():
        keywords = []
        punctuation = []
        for lit in all_standard_lits:
            content = lit[1:-1]
            if 'a' <= content[0] and content[0] <= 'z':
                keywords.append(content)
            else:
                punctuation.append(content)
        keywords.sort()
        punctuation.sort()
        emit_java_file(
            'TokenStrings',
            '''
package %(package)s;

import com.google.common.collect.ImmutableSet;

/**
 * All the tokens that appear literally in the grammar.
 */
@javax.annotation.Generated(%(generator)s)
public final class TokenStrings {

  private TokenStrings() {
    // Provides static API
  }

  /**
   * Language keywords.
   * This includes literals like {@code false} that are not part of the
   * keyword list, but are instead reserved type names.
   */
  public static final ImmutableSet<String> RESERVED = ImmutableSet.copyOf(
      new String[] { %(reserved_words)s }
  );

  /**
   * Non-alphabetic strings that appear literally in the grammar.
   * This excludes strings that appear purely in the lexical grammar like
   * whitespace characters, comment boundaries, and string/char literal
   * boundaries.
   */
  public static final ImmutableSet<String> PUNCTUATION = ImmutableSet.copyOf(
      new String[] { %(punctuation)s }
  );
}
''' % {
    'package': java_package,
    'cn_prefix': cn_prefix,
    'generator': generator,
    'reserved_words': ', '.join(_java_str_lit(s) for s in keywords),
    'punctuation': ', '.join(_java_str_lit(s) for s in punctuation),
})

    write_literal_tokens()

    def write_identifier_wrappers():
        wrappers = set(('Identifier',))
        def find_wrapper(c, p):
            variants = p['variants']
            if len(variants) == 1:
                v = variants[0]
                pt = v['ptree']
                if len(pt) == 1:
                    pt0 = pt[0]
                    if pt0['name'] == 'ref' and pt0['pleaf'][0] == 'Identifier':
                        wrappers.add(p['name'])

        for_each_prod(find_wrapper)

        # TODO: maybe just add annotations instead and let it be handled in
        # NodeTypeTables.
        emit_java_file(
            'IdentifierWrappers',
            '''
package %(package)s;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.util.EnumSet;

/**
 * The set of productions which just decorate {@link NodeType#Identifier}.
 */
@javax.annotation.Generated(%(generator)s)
final class IdentifierWrappers {

  private IdentifierWrappers() {
    // Provides static API
  }

  // We key off the classes instead of the node types to avoid
  // class-initialization cycles.
  private static final ImmutableSet<%(cn_prefix)sNodeType> IDENT_WRAPPERS =
      Sets.immutableEnumSet(EnumSet.of(
%(wrappers)s));

  /**
   * True iff the given nodetype has a single variant that simply delegate to
   * Identifier.
   */
  public static boolean isIdentifierWrapper(%(cn_prefix)sNodeType nodeType) {
    return IDENT_WRAPPERS.contains(nodeType);
  }
}
''' % {
    'package': java_package,
    'cn_prefix': cn_prefix,
    'generator': generator,
    'wrappers': ',\n'.join(
        '          %sNodeType.%s' % (cn_prefix, wrapper)
        for wrapper in sorted(wrappers)),
})

    write_identifier_wrappers()

    def write_prod_annotations():
        """
        Variant annotations are stored with the variant via method overrides.
        Since there is one big enum for all node types, it's more efficient to
        store them in EnumMap tables instead of creating a class per node type.

        This is mostly for Template handling, which is a largely separable
        concern from the Java grammar.
        """
        tables = {}  # Maps annot name to prod_name to values

        value_types = {
            'InterpGroup': ('%sNodeType' % cn_prefix, ()),
            'Interp': ('LeftRecursion.Stage',
                       ('com.mikesamuel.cil.parser.LeftRecursion',)),
            }

        def fill_tables(c, p):
            for annot_tok in p['annots']:
                (annot, (ln, co, ci)) = annot_tok
                if not _is_annotation(annot):
                    raise Exception('Bad annotation %r for %s' %
                                    (repr(annot), p['name']))

                if annot.startswith('(@'):
                    annot_name, value = annot[2:-1].split('=')
                else:
                    annot_name = annot[1:]
                    value = 'true'
                if annot_name in ('mixin',):
                    continue
                annot_name = '%s%s' % (annot_name[0].upper(), annot_name[1:])
                table = tables.get(annot_name)
                if table is None:
                    table = {}
                    tables[annot_name] = table
                table[p['name']] = value
        for_each_prod(fill_tables)

        imports = set()
        table_defs = []
        for (table_name, prod_to_value) in tables.iteritems():
            annot_name = _first_upper(table_name)
            is_set = tuple(set(prod_to_value.itervalues())) == ('true',)
            if is_set:
                imports.add('com.google.common.collect.ImmutableSet')
                imports.add('com.google.common.collect.Sets')
                imports.add('java.util.EnumSet')
                table_defs.append(
                    '''
  /** Productions annotated with <code>&#64;%(annot_name)s</code> */
  public static final ImmutableSet<%(cn_prefix)sNodeType> %(table_name_uc)s =
      Sets.immutableEnumSet(EnumSet.of(%(members)s));
''' % {
    'cn_prefix': cn_prefix,
    'annot_name': annot_name,
    'table_name': table_name,
    'table_name_uc': _camel_to_underscores(table_name),
    'members': ', '.join(['%sNodeType.%s' % (cn_prefix, pn)
                          for pn in sorted(prod_to_value.keys())]),
    })
            else:
                imports.add('com.google.common.collect.ImmutableMap')
                imports.add('com.google.common.collect.Maps')
                imports.add('java.util.EnumMap')
                value_type, value_type_imports = value_types.get(
                    table_name, (table_name, ()))
                imports.update(value_type_imports)
                puts = []
                for (key, value) in prod_to_value.iteritems():
                    puts.append(
                        ('    m.put(NodeType.%(key)s,'
                         ' %(value_type)s.%(value)s);') %
                        {
                            'key': key,
                            'value': value,
                            'value_type': value_type,
                        })
                table_defs.append(
                    '''
  /** Maps productions based on <code>&#64;%(annot_name)s</code> annotations. */
  public static final ImmutableMap<%(cn_prefix)sNodeType, %(value_type)s> %(table_name_uc)s;
  static {
    EnumMap<%(cn_prefix)sNodeType, %(value_type)s> m = new EnumMap<>(%(cn_prefix)sNodeType.class);
%(puts)s
    %(table_name_uc)s = Maps.immutableEnumMap(m);
  }
''' % {
    'annot_name': annot_name,
    'table_name': table_name,
    'table_name_uc': _camel_to_underscores(table_name),
    'value_type': value_type,
    'puts': '\n'.join(puts),
    })


        import_stmts = '\n'.join(['import %s;' % cl for cl in sorted(imports)])

        if not table_defs:
            return

        emit_java_file(
            '%sNodeTypeTables' % cn_prefix,
            '''
package %(package)s;

%(import_stmts)s

/**
 * The set of productions which just decorate {@link %(cn_prefix)sNodeType#Identifier}.
 */
@javax.annotation.Generated(%(generator)s)
public final class %(cn_prefix)sNodeTypeTables {

%(table_defs)s

}
''' % {
    'package': java_package,
    'cn_prefix': cn_prefix,
    'generator': generator,
    'import_stmts': import_stmts,
    'table_defs': ''.join(table_defs),
})

    write_prod_annotations()

    if verbose:
        # Dump the "Public API" of each chapter -- those productions that are
        # referenced from other chapters.  Does not include "CompilationUnit"
        def dump_cross_chapter_uses():
            prods_by_chapter = {}
            def bc(c, p):
                prods_by_chapter[p['name']] = c['name']
            for_each_prod(bc)

            callees_by_pn = {}
            def ptree_walker(c, caller):
                caller_pn = caller['name']
                callees = set()
                def walk_ptree(pt):
                    if 'pleaf' in pt:
                        if pt['name'] == 'ref':
                            callee_pn = pt['pleaf'][0]
                            callees.add(callee_pn)
                    else:
                        for child in pt['ptree']:
                            walk_ptree(child)
                for variant in caller['variants']:
                    walk_ptree({ 'name': '()', 'ptree': variant['ptree'] })
                callees_by_pn[caller_pn] = callees
            for_each_prod(ptree_walker)

            called_cross_chapter = {}
            for caller_pn, callees in callees_by_pn.iteritems():
                caller_cn = prods_by_chapter[caller_pn]
                for callee_pn in callees:
                    if callee_pn in BUILTINS: continue
                    callee_cn = prods_by_chapter[callee_pn]
                    if caller_cn != callee_cn:
                        s = called_cross_chapter.get(callee_cn)
                        if s is None:
                            s = set()
                            called_cross_chapter[callee_cn] = s
                        s.add(callee_pn)

            for cn, api in sorted(called_cross_chapter.items()):
                print 'Chapter %s exposes [%s]' % (cn, ', '.join(sorted(api)))

        dump_cross_chapter_uses()


if __name__ == '__main__':
    import argparse
    import os
    import os.path

    argparser = argparse.ArgumentParser()
    argparser.add_argument(
        '--grammar_name',
        help=(
            'A java identifier that is used as the sub package name and'
            ' which is used to derive the base node types for nodes in the'
            ' AST.  For example, if "j8" is supplied, the output will be put'
            ' in package %s.j8 and nodes will be a subtype of J8BaseNode and'
            ' the node type enum will be J8NodeType and variants will be of'
            ' type J8NodeVariant.'
        ))
    argparser.add_argument(
        '--grammar_customizations',
        help=(
            'Path to a JSON file that defines the mixins and custom node'
            ' content for the grammar.'
        ))
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

    grammar_name = args.grammar_name

    package_path = tuple(_JAVA_BASE_PACKAGE.split('.') + [grammar_name])

    with file(args.grammar_customizations) as json_file:
        grammar_customizations = json.load(json_file)

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
        grammar_name=grammar_name,
        grammar_customizations=grammar_customizations,
        grammar_text=open(args.grammar_file, 'r').read(),
        source_file_exists=source_file_exists,
        emit_java_file=emit_java_file,
        dot_out=args.dotout,
        verbose=args.v)
